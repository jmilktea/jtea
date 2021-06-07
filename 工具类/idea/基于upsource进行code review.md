upsource是jetbrains开发的code review工具，可以方便的进行代码分析，审核，点评等，同时拥有配套的权限和角色管理，是一个非常不错的代码审核工具。  
一般我们使用idea开发，还可以搭配装一个upsource插件，可以在upsource上的点评就会同步到开发本地。  
github/gitlab也有类似的功能，不过使用起来没那么方便。  

## upsource安装  
官方下载安装包：https://www.jetbrains.com/upsource/，这个包有1G那么大，免费版本只能有10个用户使用，适合小团队使用。如果项目多又想使用免费版本，可以搭建多个upsource即可。

下载完成后我们上传到服务器，进入bin目录后，使用./upsource.sh命令启动服务。服务初始化需要一定时间，按照提示填写相关信息即可。  
完成后可以看到如下界面，选择create project可以创建项目  
![image](https://github.com/jmilktea/jmilktea/blob/master/%E5%B7%A5%E5%85%B7%E7%B1%BB/idea/images/upsource-1.png)  

填写项目的基本信息，使用gitlab就选择它，填写验证信息，这样就可以把我们的代码导入到upsource了，导入到我们可以看到代码仓库的commit记录，代码分支等。    
![image](https://github.com/jmilktea/jmilktea/blob/master/%E5%B7%A5%E5%85%B7%E7%B1%BB/idea/images/upsource-2.png)  

选择compare即可以进行分组对比  
![image](https://github.com/jmilktea/jmilktea/blob/master/%E5%B7%A5%E5%85%B7%E7%B1%BB/idea/images/upsource-3.png)  

看到有不规范的代码，reviewer可以对代码进行点评  
![image](https://github.com/jmilktea/jmilktea/blob/master/%E5%B7%A5%E5%85%B7%E7%B1%BB/idea/images/upsource-4.png)  

接着可以添加一些账号，给开发人员登录使用  
![image](https://github.com/jmilktea/jmilktea/blob/master/%E5%B7%A5%E5%85%B7%E7%B1%BB/idea/images/upsource-5.png)  

## idea upsource插件  
上面code reviewer对代码进行审核点评后，如何通知到开发人员呢，我们希望它能直接显示到对应开发的idea工具上，upsource插件就可以实现这个功能。  
安装好插件后，填入upsource工具地址，选择接收通知    
![image](https://github.com/jmilktea/jmilktea/blob/master/%E5%B7%A5%E5%85%B7%E7%B1%BB/idea/images/upsource-6.png)  
![image](https://github.com/jmilktea/jmilktea/blob/master/%E5%B7%A5%E5%85%B7%E7%B1%BB/idea/images/upsource-7.png)  

插件安装好后，idea的右侧工具栏和右下角就会显示对应图标  
![image](https://github.com/jmilktea/jmilktea/blob/master/%E5%B7%A5%E5%85%B7%E7%B1%BB/idea/images/upsource-8.png)  

右下角可以切换项目和用户，选择切换用户后，会弹出上面我们搭建的upsource地址提示我们登陆，我们使用创建好的账号登陆即可  
![image](https://github.com/jmilktea/jmilktea/blob/master/%E5%B7%A5%E5%85%B7%E7%B1%BB/idea/images/upsource-9.png)  

接着corereviewer在upsoruce上对代码进行审核评论，就可以同步到开发本地idea上了，开发可以本地进行讨论和解决，解决好后点击resolved即可完成本次讨论  
![image](https://github.com/jmilktea/jmilktea/blob/master/%E5%B7%A5%E5%85%B7%E7%B1%BB/idea/images/upsoruce-10.png)  
![image](https://github.com/jmilktea/jmilktea/blob/master/%E5%B7%A5%E5%85%B7%E7%B1%BB/idea/images/upsource-11.png)  
![image](https://github.com/jmilktea/jmilktea/blob/master/%E5%B7%A5%E5%85%B7%E7%B1%BB/idea/images/upsource-12.png)  
