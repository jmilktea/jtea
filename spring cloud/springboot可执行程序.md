## 简介
使用springboot开发，通常开发完成后我们会使用maven或gradle将应用程序打包成一个jar包，然后使用java -jar命令运行我们的程序。  
本篇要介绍的是另一种方式运行springboot程序，把它作为Unix/Linux system的可执行程序运行，也就是将其视为一个系统的应用程序，这样可以使用系统的相关工具来进行管理。java -jar这种方式对于java程序员来说更加熟悉，而打包成linux可执行程序对于运维同学来说可能更加友好，因为他们管理的是面向系统进程，而不仅仅是java应用，当我们的应用场景变得复杂时，这种方式就会变得有用，例如应用需要随开机启动，应用有依赖进程等，这些可以通过linux进程的管理来实现。

springboot已经帮我们实现了这个功能，只需要简单的配置：  
maven
```
<plugin>
	<groupId>org.springframework.boot</groupId>
	<artifactId>spring-boot-maven-plugin</artifactId>
	<configuration>
		<executable>true</executable>
	</configuration>
</plugin>
```
gradle
```
bootJar {
	launchScript()
}
```

通过如上配置，打包完成后即可使用./app.jar start运行程序，如果我们需要加一些应用的启动参数呢？在app的同级目录下新建一个同名的app.conf，新增配置   
```
MODE=service
JAVA_OPTS="-Xms256m -Xmx256M"
PID_FOLDER=./
LOG_FOLDER=/data/logs/app.log
```
通过ps -ef 查看进程，可以看到还是使用了java -jar 命名，只不过springboot打包程序帮我们做了一层封装。   
这种方式在常用linux发行版，如centos,ubuntu可以很好的支持，如果是OS X,FreeBSD需要做一些额外的处理。另外如果是windows server可以使用winsw将springboot应用作为windows service进行管理，[参考这里](https://github.com/snicoll/spring-boot-daemon)    

## init.d/systemd      
**init**进程是linux中的初始化进程，其它服务和进程都是通过它来启动的，init.d是init进程的启动目录，位置在/etc/init.d，linux在启动的时候会找到该目录下的脚本执行，同时该目录下的脚本能够响应start/stop/restart/reload等命令来管理某个具体的应用程序。   
**systemd**是init的改进，更加高效和智能，按照linux惯例，d是daemon的意思，systemd用于守护整个系统。systemd可以并行启动进程，加快启动速度，它还可以解析服务的依赖性，并自动启动依赖服务。最新版本的linux发行版都采用了systemd作为初始进程。[systemd介绍参考](https://www.ruanyifeng.com/blog/2016/03/systemd-tutorial-commands.html)      
上面我们说到把springboot打包成可执行程序，本质上还是将其安装为init.d或者systemd的服务运行。   
**安装为init.d服务**  
```
sudo ln -s /var/myapp/myapp.jar /etc/init.d/myapp
```
就可以支持start,stop,restart,status等命令，如
```
service myapp start
./myapp start  
```
同时支持如下特性：   
1.对jar进行用户权限控制  
2.将进程id写入到/var/run/<appname>/<appname>.pid文件，方便跟踪  
3.将控制台日志输出到Writes console logs to /var/log/<appname>.log文件    

**安装为systemd服务**   
在/etc/systemd/system创建一个myapp.service文件，指向我们的jar包    
```
[Unit]
Description=myapp

[Service]
User=myapp
ExecStart=/var/myapp/myapp.jar
```
启动服务
```
systemctl start myapp.service    
```
systemd的功能和命令非常多，需要时自行查找使用即可。   

## springboot实现原理    
回到最开始的位置，我们新建一个同名的app.conf，在里面写上一些配置就可以被解析到作为启动参数，这是为什么呢？MODE=service这些参数又是什么含义呢？   
当我们使用gradle或者maven package时，会使用JarWriter去生成jar包，如下  
```
public JarWriter(File file, LaunchScript launchScript)
    throws FileNotFoundException, IOException {
  FileOutputStream fileOutputStream = new FileOutputStream(file);
  if (launchScript != null) {
    fileOutputStream.write(launchScript.toByteArray());
    setExecutableFilePermission(file);
  }
  this.jarOutput = new JarArchiveOutputStream(fileOutputStream);
  this.jarOutput.setEncoding("UTF-8");
}
```
其中将启动脚本launchScript写入文件流，这个对象默认是DefaultLaunchScript，它会去加载launch.script文件   
```
public DefaultLaunchScript(File file, Map<?, ?> properties) throws IOException {
  String content = loadContent(file);
  this.content = expandPlaceholders(content, properties);
}

private String loadContent(File file) throws IOException {
  if (file == null) {
    return loadContent(getClass().getResourceAsStream("launch.script"));
  }
  return loadContent(new FileInputStream(file));
}
```   
launch.script文件的内容在[这里](https://github.com/spring-projects/spring-boot/blob/main/spring-boot-project/spring-boot-tools/spring-boot-loader-tools/src/main/resources/org/springframework/boot/loader/tools/launch.script)   
![image](https://github.com/jmilktea/jmilktea/blob/master/spring%20cloud/images/sb-exe1.png)    
代码比较多，但读起来还是比较简单，我们可以看到一些关键字，launch脚本默认回去解析jar包同名的.conf文件，里面配置的参数都会被解析出来，其中JAVA_OPTS参数会被当做java -jar的启动参数。   
我们可以在构建应用时设置一些构建时的参数，通过embeddedLaunchScriptProperties参数设置
```
<configuration>
    <executable>true</executable>
    <embeddedLaunchScriptProperties>
        //配置需要的参数
    </embeddedLaunchScriptProperties>
</configuration>
```
也可以像上面一样在配置运行时需要的参数，详细配置参考：[Installing Spring Boot Applications](https://docs.spring.io/spring-boot/docs/2.0.x/reference/html/deployment-install.html)    






