## 背景   
公司在海外收购了一家银行，有一个正在运行的项目，也需要部署在该银行系统的云上，银行系统合规要求比较严格，有如下要求：
- 源码必须单独托管在银行系统所在的gitlab仓库     
- 文件不允许出现现在公司的名称
- 代码中不允许出现现在公司的名称
- 注释不允许出现中文   
...   
一份代码推送到不同的仓库是常见的，类似于代码同步到github和gitee，但我们的场景是推送到其中一个仓库时还掉去掉一些关键字文件和关键字，以满足银行合规要求。    

起初我们的做法是在银行仓库创建分支，然后merge到开发分支过来，随着代码和文件的删改，merge会把历史的带过来，需要再银行分支再处理掉，merge变得不可行。   
后来使用了cherry-pick，cherry-pick可以把某些commit的内容合并到另一个分支，这样就不会把历史的合并过来了，但随着时间的推移，cherry-pick也变得无能为力，例如中文注释对于我们开发人员是必不可少的，每次都会合并过来，需要手动删掉，这种手动的处理会变得麻烦，而且容易漏错。    
![image](https://github.com/jmilktea/jmilktea/blob/master/%E5%B7%A5%E5%85%B7%E7%B1%BB/git/images/git-hook1.png)

我们的目标是：一份代码，自动合规。   
在一个仓库的分支上开发，合并到另一个仓库的分支，提交时，代码自动进行合规化，不需要手动修改。

## 实现思路   
合规要求删掉一些关键信息，那能不能用程序来实现呢？那么问题就变成：在哪个环节去实现，用什么实现的问题。    
要在代码推送到远端前，对代码进行处理，git提供了**githook**可以对git的操作环节进行拦截，默认在我们的代码仓库.git/hooks目录下，有很多的hook sample文件，这些.sample文件里面是shell脚本，是不会执行的，如果去掉.sample就会在对应的环节执行。githook可以是shell或者python等脚本语言，我们需要操作git分支和文件等，使用python会更加方便。   
更多的githook可以参考[git官方说明](https://git-scm.com/book/en/v2/Customizing-Git-Git-Hooks)   

这里我们选用的是pre-commit hook，在commit之前对代码进行处理，这是个很熟悉的操作，idea在提交代码前，可以选择对代码进行reformat code，防止我们忘记格式化代码，实际上道理是一样的。    
经过分析后，我们的流程变成了    
![image](https://github.com/jmilktea/jmilktea/blob/master/%E5%B7%A5%E5%85%B7%E7%B1%BB/git/images/githook2.png)    
git hook合规脚本就是对commit内容就行格式化的过程，这个思路很像我们的http请求经过很多filter的处理后，达到我们的contrller，变成我们想要的东西。

实现流程步骤是：   
1.在pre-commit hook中，拿到本次commit修改的文件    
2.判断文件名称，是否需要删除文件   
3.读取文件内容，删掉代码段和中文注释     
代码段是一个定义要删除的代码片段，如下，//region remove 包含的内容会被删除     
```
int i = 10;
//region remove
if(i > 1) {
    //逻辑
}
//end region remove

变成
int i = 10;
```
4.将修改后的变动重新添加到git index索引，如果没这一步，相当于又修改了文件，git会要求再次commit    

**python如何与git交互**    
下方源码可以看到，我们使用的是gitpython，由于平时不常写python，也是现学现用，要怎么找到这个库呢。github是非常好用的，我们直接在github搜索python git就可以看到有很多python与git交互的代码库，接下来就选择一个满足要求的就行了。    

## 源码     
```
#!/usr/bin/env python
#coding=utf-8
 
import sys,os,re
from git import Repo

repo = Repo('.');
if(repo.active_branch.name.find("_bank") != -1):       
    print("start analysis...")
    diff_list = repo.head.commit.diff()
    special_file_name = ['company_name1','company_name2']
    chinese_pattern = "[\u4e00-\u9fa5]"
    start_remove = "//region remove"
    end_remove = "//end region remove"

    for diff in diff_list:
        path = os.getcwd() + "/" + diff.b_path
        with open(path,'r+',encoding='utf-8') as file:
            file_name = os.path.basename(path)
            file_remove = 0
            print("start analysis:" + file_name)
            for special in special_file_name:         
                # 包含特殊文件名称，删除文件
                if(file_name.lower().find(special) != -1):
                    print("removing:" + file_name)
                    file.close()
                    os.remove(path)            
                    repo.index.remove(path)
                    file_remove = 1
                    break
            if file_remove == 0:
                repalce = 0            
                content = file.read()
                # 处理代码块    
                print("repalcing region:" + file_name)
                while(content.find(start_remove) != -1):                                
                    if(content.find(end_remove) != -1):
                        repalce = 1
                        start_index = content.index(start_remove)
                        end_index = content.index(end_remove) + len(end_remove) - 1
                        content = content[:start_index - 1] + content[end_index + 1:]
                
                # 处理中文注释
                print("repalcing chinese:" + file_name)            
                if re.search(chinese_pattern,content):
                    repalce = 1
                    content = re.sub(chinese_pattern,"",content)            
                
                if repalce == 1:
                    print("repalcing...")
                    file.seek(0)
                    file.truncate()
                    file.write(content)
                    file.close()
                    repo.index.add(path)
                    
            print("end analysis:" + file_name)
            
    print("end analysis...")
        
```
