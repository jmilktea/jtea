## 背景
目前有一个项目的gitflow大概是这样的   
1.开发人员从master checkout feature分支进行开发   
2.开发完成后合并到release分支进行测试   
3.测试完成后使用release分支进行发版  
4.发版完成，业务验收通过后，将release分支合并回master    
5.开发人员将最新master合并到目前还在开发的feature分支    

在release合并回master期间，可能有新的feature分支从master checkou开发，这部分feature分支是没有release最新代码的。   
目前的解决方式是，合并回master后，由开发负责人通知所有开发，让开发合并最新master回当前正在开发的feature分支。   
但是这个可能会漏掉，忘记合并，导致漏掉上次发版代码，造成生产事故。目前运维在发布release的时候，也会再次校验是否合并了最新的master，如果没有就终止发版。   

这里我的想法是要实现：使用idea来通知开发哪些仓库有变更，需要关注。      
1.master有变更了，idea发出通知    
2.发版前，检查发版分支是否已经合并最新的master，如果没有，idea发出通知    

## 实现   
**实现思路**    
1.判断master分支最后一个commit是不是当天的，是就表示当天有更新，需要通知，通知后将本次commit id缓存，表示通知过，后面不再通知。  
2.检查当天是否有release分支，有则表示当天要发版，同样获取master最后一个commit，判断release分支有没有包含master最后一个commit，没有就通知。 

要实现这个目标需要解决两个问题   
1.需要感知git远端仓库的变更，我们使用gitlab，所以需要能查询gitlab仓库的信息    
2.开发一个idea插件，做通知变更     

对于问题1，我们使用[gitlab4j](https://github.com/gitlab4j/gitlab4j-api#commitsapi)操作gitlab，有很丰富的api，可以获取仓库分支，获取分支的commit记录等。     
对于问题2，需要开发一个idea插件，在idea弹框通知开发人员        

**idea 插件开发**    
idea插件开发入门可以参考      
idea插件一个简单的demo，https://juejin.cn/post/6916053498480033806    
也可以参考下这篇文章，https://cloud.tencent.com/developer/article/1348741    

idea插件基于IntelliJ Platform开发，idea本身也是通过它开发出来。    
首先需要安装Plugin DevKit插件，安装完成后，new project 时会出现IntelliJ Platform Plugin选项，用于创建插件开发工程。   
工程目录与普通的spring项目类似，plugin.xml用于对插件进行一些描述，例如插件的名称、版本、作者、有哪些组件等。         
![iamge](3)    

导包  
不像普通项目直接通过pom导包，插件开发需要新建一个lib目录，将jar包放到该目录，然后右键Add as Library，这样工程才可以使用   
调式/运行          
debug或者run，idea会弹出一个新窗口，该窗口就是包含了该插件的工程，可以在这个工程看到插件的效果    
日志   
运行期间，日志会打印在控制台的idea.log窗口     
打包  
选择build → prepare plugin module x for deployment 就可以发布插件了，发布后会生成一个.zip，用于安装   

**效果**    
1.设置accessToken   
访问gitlab需要权限，需要使用个人的token访问，到gitlab settings -> access token 生成一个token，用于访问gitlab。

2.配置accessToken   
在user.home路径下新增一个access-token.txt文件，内容为1生成的token，后面会被我们程序读取使用。     
windows下user.home就是当前用户目录。    

3.下载插件    
按上面开发打包好的插件，也可以在[这里下载idea-gitlab-notify]()体验，插件我们最终是打包成一个zip包   

4.安装插件    
idea → setting → plugins → install plugin from disk   
选择zip安装包   

5.效果   
![image](1)   
![image](2)   
当master有变更，或者发版日发现发版分支未合并master，idea就会做出如上通知。    
notify通知会通知打印在event log日志、左下角提示语、右下角弹出框。   

**源码**    
源码如下，可以基于此去扩展，实现更多想法和功能    
```
public class MyComponent implements ProjectComponent {

	private final static Map<Integer, String> PROJECT = new HashMap<>();
	private final static Map<Integer, Map<String, Set<String>>> MASTER_NOTIFY_RESULT = new HashMap<>();
	private final static Map<Integer, Map<String, Set<String>>> RELEASE_NOTIFY_RESULT = new HashMap<>();
	private final static SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyyMMdd");
	private final static SimpleDateFormat HOUR_MIN_TIME_FORMAT = new SimpleDateFormat("HHmm");
	private final static NotificationGroup MASTER_NOTIFY_GROUP = new NotificationGroup("masterNotify", NotificationDisplayType.STICKY_BALLOON, true);
	private final static NotificationGroup RELEASE_NOTIFY_GROUP = new NotificationGroup("releaseNotify", NotificationDisplayType.STICKY_BALLOON, true);

	@Override
	public void initComponent() {
		initProject();
		Timer timer = new Timer();
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				/**
				 * 实现逻辑
				 * 1.每隔5min拉取远端master分支commit，判断是否今天提交，如果是，判断今天是否有通知过，如果没有则notify
				 * 2.检查当天是否有release分支，有，如果没合并master，在9.50后发出一次通知
				 */
				//user.home读取gitlab accessToke
				String path = System.getProperty("user.home") + File.separatorChar + "access-token.txt";
				byte[] bytes = new byte[0];
				try {
					bytes = Files.readAllBytes(Paths.get(path));
				} catch (IOException e) {
				}
				String accessToken = new String(bytes, StandardCharsets.UTF_8);
				if (accessToken == null || accessToken.isEmpty()) {
					return;
				}
				String today = DATE_FORMAT.format(new Date());
				String hourMin = HOUR_MIN_TIME_FORMAT.format(new Date());
				String masterNotifyContent = "";
				String releaseNotifyContent = "";
				//遍历所有项目
				for (Map.Entry<Integer, String> entry : PROJECT.entrySet()) {
					GitlabAPI gitlabAPI = GitlabAPI.connect("your gitlab host", accessToken);
					try {
						//读取master最新一次commit
						Pagination pagination = new Pagination();
						pagination.withPerPage(1);
						List<GitlabCommit> masterCommits = gitlabAPI.getCommits(entry.getKey(), pagination, "master");
						if (masterCommits != null && masterCommits.size() > 0) {
							GitlabCommit masterLastCommit = masterCommits.get(0);
							if (DATE_FORMAT.format(masterLastCommit.getCommittedDate()).equals(today)) {
								//master今日有变更
								Map<String, Set<String>> result = MASTER_NOTIFY_RESULT.get(entry.getKey());
								if (result == null) {
									Set<String> notifyLogs = new HashSet<>();
									notifyLogs.add(masterLastCommit.getId());
									Map<String, Set<String>> map = new HashMap<>();
									map.put(today, notifyLogs);
									MASTER_NOTIFY_RESULT.put(entry.getKey(), map);
									masterNotifyContent += entry.getValue() + " ";
								} else {
									Set<String> todayLogs = result.get(today);
									todayLogs = todayLogs == null ? new HashSet<>() : todayLogs;
									if (!todayLogs.contains(masterLastCommit.getId())) {
										//没有通知过，加入通知
										masterNotifyContent += entry.getValue() + " ";
										todayLogs.add(masterLastCommit.getId());
									}
								}
							}
							//今日有release发版分支，检查是否合并master最后一个commit，9.50~18.00间发出一个通知
							String releaseBranch = "release_" + today + "_v1";
							GitlabBranch todayReleaseBranch = null;
							try {
								todayReleaseBranch = gitlabAPI.getBranch(entry.getKey(), releaseBranch);
							} catch (Exception e) {
								e.printStackTrace();
							}
							if (masterLastCommit != null && todayReleaseBranch != null && Integer.valueOf(hourMin) >= 950 && Integer.valueOf(hourMin) < 1800) {
								pagination.withPerPage(100);
								List<GitlabCommit> releaseCommits = gitlabAPI.getCommits(entry.getKey(), pagination, releaseBranch);
								List<String> releaseCommitIds = releaseCommits.stream().map(s -> s.getId()).collect(Collectors.toList());
								if (!releaseCommitIds.contains(masterLastCommit.getId())) {
									//release还未合并master最后一个commit
									Map<String, Set<String>> result = RELEASE_NOTIFY_RESULT.get(entry.getKey());
									if (result == null) {
										Set<String> notifyLogs = new HashSet<>();
										notifyLogs.add(masterLastCommit.getId());
										Map<String, Set<String>> map = new HashMap<>();
										map.put(today, notifyLogs);
										RELEASE_NOTIFY_RESULT.put(entry.getKey(), map);
										releaseNotifyContent += entry.getValue() + " ";
									} else {
										Set<String> todayLogs = result.get(today);
										todayLogs = todayLogs == null ? new HashSet<>() : todayLogs;
										if (!todayLogs.contains(masterLastCommit.getId())) {
											todayLogs.add(masterLastCommit.getId());
											releaseNotifyContent += entry.getValue() + " ";
										}
									}
								}
							}
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				if (!masterNotifyContent.isEmpty()) {
					masterNotifyContent += "master今天有变更,请关注";
					Notification notification = MASTER_NOTIFY_GROUP.createNotification(masterNotifyContent, NotificationType.WARNING);
					Notifications.Bus.notify(notification);
				}
				if (!releaseNotifyContent.isEmpty()) {
					releaseNotifyContent += "release未合master,请关注";
					Notification notification = RELEASE_NOTIFY_GROUP.createNotification(releaseNotifyContent, NotificationType.WARNING);
					Notifications.Bus.notify(notification);
				}
			}
		}, 0, 60000 * 5);

	}

	private void initProject() {
		PROJECT.put(5143, "your-project-1");
		PROJECT.put(4335, "your-project-2");
	}
}
```


