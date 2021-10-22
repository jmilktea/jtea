我们发现有一条很简单的sql执行很慢，耗时超过30s，如下：  
```
SELECT*FROM t_table t WHERE c_uid=1878 AND STATUS IN(1,2) ORDER BY start_time LIMIT 10   
```
其中c_uid和start_time分别建了两个索引，表中的数据大概800w，符合c_uid=1878的数据大概6000，同时符合status in(1,2)的大概250，mysql版本是5.7   

开始我们拿到客户端执行一下   
```
SELECT*FROM t_table t WHERE c_uid=1878 AND STATUS IN(1,2) ORDER BY start_time LIMIT 1000    
```
发现执行并不慢，只需要几十毫秒。explain看一下发现也走了c_uid这个索引   
```
explain SELECT*FROM t_table t WHERE c_uid=1878 AND STATUS IN(1,2) ORDER BY start_time LIMIT 1000             
```
![image](https://github.com/jmilktea/jmilktea/blob/master/mysql/images/orderby-1.png)   
与生产的唯一区别就是limit的数量不一样，生产每次只会查10条，难道limit数量10就会慢？？？我们看下执行计划    
```
explain SELECT*FROM t_table t WHERE c_uid=1878 AND STATUS IN(1,2) ORDER BY start_time LIMIT 10             
```   
![image](https://github.com/jmilktea/jmilktea/blob/master/mysql/images/orderby-2.png)  
发现执行计划确认变了，在limit 10的情况下，mysql放弃了c_uid这个索引，选择了start_time这个索引，进而进行了type=index的索引扫描，我们知道索引扫描和表扫描是半斤八两，一个是扫整个表，一个是扫整个索引树，效率都非常低。

**那为什么mysql会做出错误的选择?**    
我们考虑是数据分布的问题，我们知道mysql优化器会根据数据的分布可能生成不同的执行计划，之前也遇到类似的情况，[参考这里](https://github.com/jmilktea/jmilktea/blob/master/mysql/%E8%BF%99%E4%B9%88%E5%88%86%E9%A1%B5%E6%9F%A5%E6%95%B0%E6%8D%AE%E5%B1%85%E7%84%B6%E9%87%8D%E5%A4%8D%E4%BA%86.md)，就算是相同的sql语句，mysql也可能会根据数据分布的情况生成不同的执行计划，这个执行是mysql认为最优的，但事实不一定是最优的。我们如果换一个cuid没那么多的数据，例如只有200个左右，这个时候limit 10,limit 100，就生成了一样的执行计划，这个是个大坑，很容易就踩到，也可以认为是mysql优化器的一个bug，因为它确实选错了。mysql优化器在遇到order by的时候，会对执行计划再进行一次判断，当它认为order by锁选择的索引要由于第一步选择的索引时，就会改变执行计划，这就导致的执行计划的改变，反而更加糟糕。order by导致索引选择错误有很多讨论，例如：https://bugs.mysql.com/bug.php?id=93845，https://developer.aliyun.com/article/51065        
总之，在数据库进行order by时要非常小心，很容易出事故     

**问题发生了，怎么解决好呢？**     
1.forec index强制走我们的索引，既然mysql优化器“不够完美”，我们就强制指定一下，force index后，mysql会直接选择这个索引，不过这对代码有一定的侵入性   
2.业务改写，是否可以把所有数据都加载出来在内存排序，数据量不大的情况是可以的    
3.mysql8，如果使用8的话，mysql已经知道这个问题，但是他没有修复好，而是提供一个参数禁止这个优化，参数为： prefer_ordering_index 来关闭首选排序索引这个优化过程      

## 深入执行计划   
上面我们从explain看到了最终的执行计划，还可以查看详细的执行计划，具体的步骤如下：   
开启优化器跟踪开关  
```
set session optimizer_trace="enabled=on";
``` 
执行explain   
```
EXPLAIN SELECT*FROM t_case tc WHERE person_uuid='fd6c0f405e694163c795391566e50496' order by create_time DESC LIMIT 10;
```
查看跟踪结果
```
SELECT * FROM INFORMATION_SCHEMA.OPTIMIZER_TRACE;
```
输出的内容比较多，是mysql优化器优化的每一个步骤，我们放在最后。其中有一段关键     
```
 {
            ""reconsidering_access_paths_for_index_ordering"": {
              ""clause"": ""ORDER BY"",
              ""steps"": [
              ],
              ""index_order_summary"": {
                ""table"": ""`t_offline_task` `tot`"",
                ""index_provides_order"": true,
                ""order_direction"": ""asc"",
                ""index"": ""idx_start_time"",
                ""plan_changed"": true,
                ""access_type"": ""index""
              }
            }
          }
```    
**reconsidering_access_paths_for_index_ordering** 就是问题所在，在这个步骤mysql结合order by的索引重新考虑了执行计划，并且做出了改变，选择了idx_start_time这个索引。
详细的步骤如下   
```
"TRACE"
"{
  ""steps"": [
    {
      ""join_preparation"": {
        ""select#"": 1,
        ""steps"": [
          {
            ""IN_uses_bisection"": true
          },
          {
            ""expanded_query"": ""/* select#1 */ select `tot`.`id` AS `id`,`tot`.`case_id` AS `case_id`,`tot`.`job_id` AS `job_id`,`tot`.`person_uuid` AS `person_uuid`,`tot`.`queue_code` AS `queue_code`,`tot`.`c_uid` AS `c_uid`,`tot`.`ptp_extend_day` AS `ptp_extend_day`,`tot`.`distribute_type` AS `distribute_type`,`tot`.`dpd` AS `dpd`,`tot`.`debt` AS `debt`,`tot`.`status` AS `status`,`tot`.`finish_status` AS `finish_status`,`tot`.`promise_status` AS `promise_status`,`tot`.`start_time` AS `start_time`,`tot`.`end_time` AS `end_time`,`tot`.`operate_by` AS `operate_by`,`tot`.`update_time` AS `update_time`,`tot`.`create_time` AS `create_time`,`tot`.`valid_start_time` AS `valid_start_time`,`tot`.`valid_end_time` AS `valid_end_time`,`tot`.`source` AS `source`,`tot`.`is_stop` AS `is_stop`,`tot`.`case_type` AS `case_type`,`tot`.`company_id` AS `company_id` from `t_offline_task` `tot` where ((`tot`.`c_uid` = 1878) and (`tot`.`status` in (1,2))) order by `tot`.`start_time` limit 10""
          }
        ]
      }
    },
    {
      ""join_optimization"": {
        ""select#"": 1,
        ""steps"": [
          {
            ""condition_processing"": {
              ""condition"": ""WHERE"",
              ""original_condition"": ""((`tot`.`c_uid` = 1878) and (`tot`.`status` in (1,2)))"",
              ""steps"": [
                {
                  ""transformation"": ""equality_propagation"",
                  ""resulting_condition"": ""((`tot`.`status` in (1,2)) and multiple equal(1878, `tot`.`c_uid`))""
                },
                {
                  ""transformation"": ""constant_propagation"",
                  ""resulting_condition"": ""((`tot`.`status` in (1,2)) and multiple equal(1878, `tot`.`c_uid`))""
                },
                {
                  ""transformation"": ""trivial_condition_removal"",
                  ""resulting_condition"": ""((`tot`.`status` in (1,2)) and multiple equal(1878, `tot`.`c_uid`))""
                }
              ]
            }
          },
          {
            ""substitute_generated_columns"": {
            }
          },
          {
            ""table_dependencies"": [
              {
                ""table"": ""`t_offline_task` `tot`"",
                ""row_may_be_null"": false,
                ""map_bit"": 0,
                ""depends_on_map_bits"": [
                ]
              }
            ]
          },
          {
            ""ref_optimizer_key_uses"": [
              {
                ""table"": ""`t_offline_task` `tot`"",
                ""field"": ""c_uid"",
                ""equals"": ""1878"",
                ""null_rejecting"": false
              }
            ]
          },
          {
            ""rows_estimation"": [
              {
                ""table"": ""`t_offline_task` `tot`"",
                ""range_analysis"": {
                  ""table_scan"": {
                    ""rows"": 7003226,
                    ""cost"": 1.51e6
                  },
                  ""potential_range_indexes"": [
                    {
                      ""index"": ""PRIMARY"",
                      ""usable"": false,
                      ""cause"": ""not_applicable""
                    },
                    {
                      ""index"": ""uni_case_id_job_id"",
                      ""usable"": false,
                      ""cause"": ""not_applicable""
                    },
                    {
                      ""index"": ""idx_c_uid"",
                      ""usable"": true,
                      ""key_parts"": [
                        ""c_uid"",
                        ""id""
                      ]
                    },
                    {
                      ""index"": ""idx_start_time"",
                      ""usable"": false,
                      ""cause"": ""not_applicable""
                    },
                    {
                      ""index"": ""idx_source"",
                      ""usable"": false,
                      ""cause"": ""not_applicable""
                    },
                    {
                      ""index"": ""idx_person_uuid"",
                      ""usable"": false,
                      ""cause"": ""not_applicable""
                    },
                    {
                      ""index"": ""idx_update_time"",
                      ""usable"": false,
                      ""cause"": ""not_applicable""
                    }
                  ],
                  ""setup_range_conditions"": [
                  ],
                  ""group_index_range"": {
                    ""chosen"": false,
                    ""cause"": ""not_group_by_or_distinct""
                  },
                  ""analyzing_range_alternatives"": {
                    ""range_scan_alternatives"": [
                      {
                        ""index"": ""idx_c_uid"",
                        ""ranges"": [
                          ""1878 <= c_uid <= 1878""
                        ],
                        ""index_dives_for_eq_ranges"": true,
                        ""rowid_ordered"": true,
                        ""using_mrr"": false,
                        ""index_only"": false,
                        ""rows"": 11888,
                        ""cost"": 14267,
                        ""chosen"": true
                      }
                    ],
                    ""analyzing_roworder_intersect"": {
                      ""usable"": false,
                      ""cause"": ""too_few_roworder_scans""
                    }
                  },
                  ""chosen_range_access_summary"": {
                    ""range_access_plan"": {
                      ""type"": ""range_scan"",
                      ""index"": ""idx_c_uid"",
                      ""rows"": 11888,
                      ""ranges"": [
                        ""1878 <= c_uid <= 1878""
                      ]
                    },
                    ""rows_for_plan"": 11888,
                    ""cost_for_plan"": 14267,
                    ""chosen"": true
                  }
                }
              }
            ]
          },
          {
            ""considered_execution_plans"": [
              {
                ""plan_prefix"": [
                ],
                ""table"": ""`t_offline_task` `tot`"",
                ""best_access_path"": {
                  ""considered_access_paths"": [
                    {
                      ""access_type"": ""ref"",
                      ""index"": ""idx_c_uid"",
                      ""rows"": 11888,
                      ""cost"": 14266,
                      ""chosen"": true
                    },
                    {
                      ""access_type"": ""range"",
                      ""range_details"": {
                        ""used_index"": ""idx_c_uid""
                      },
                      ""chosen"": false,
                      ""cause"": ""heuristic_index_cheaper""
                    }
                  ]
                },
                ""condition_filtering_pct"": 20,
                ""rows_for_plan"": 2377.6,
                ""cost_for_plan"": 14266,
                ""chosen"": true
              }
            ]
          },
          {
            ""attaching_conditions_to_tables"": {
              ""original_condition"": ""((`tot`.`c_uid` = 1878) and (`tot`.`status` in (1,2)))"",
              ""attached_conditions_computation"": [
              ],
              ""attached_conditions_summary"": [
                {
                  ""table"": ""`t_offline_task` `tot`"",
                  ""attached"": ""(`tot`.`status` in (1,2))""
                }
              ]
            }
          },
          {
            ""clause_processing"": {
              ""clause"": ""ORDER BY"",
              ""original_clause"": ""`tot`.`start_time`"",
              ""items"": [
                {
                  ""item"": ""`tot`.`start_time`""
                }
              ],
              ""resulting_clause_is_simple"": true,
              ""resulting_clause"": ""`tot`.`start_time`""
            }
          },
          {
            ""added_back_ref_condition"": ""((`tot`.`c_uid` <=> 1878) and (`tot`.`status` in (1,2)))""
          },
          {
            ""reconsidering_access_paths_for_index_ordering"": {
              ""clause"": ""ORDER BY"",
              ""steps"": [
              ],
              ""index_order_summary"": {
                ""table"": ""`t_offline_task` `tot`"",
                ""index_provides_order"": true,
                ""order_direction"": ""asc"",
                ""index"": ""idx_start_time"",
                ""plan_changed"": true,
                ""access_type"": ""index""
              }
            }
          },
          {
            ""refine_plan"": [
              {
                ""table"": ""`t_offline_task` `tot`""
              }
            ]
          }
        ]
      }
    },
    {
      ""join_explain"": {
        ""select#"": 1,
        ""steps"": [
        ]
      }
    }
  ]
}"
```
