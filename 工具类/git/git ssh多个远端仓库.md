一份代码托管到远端仓库是非常常见的情况，例如有时候出于审计要求，系统需要单独部署，源码也需要单独托管，所以我们开发需要将代码推送到多个远端仓库。还有更常见的我们的文章或源码可以同时托管到github、gitlab或gitee，接下来就看一下怎么使用git实现这个功能。    

**必要条件**  
- 本地安装git     
- 注册托管平台账号    

在clone仓库时，有ssh和https两种方式，这里介绍的是使用ssh的方式，ssh的方式就是在本地生成一个公钥，类似token机制，然后把公钥配置到你的托管平台账号下，表示该账号允许使用该公钥进行表示。     

1. 在git安装目录下，打开git-bash.exe控制台，使用你的账号生成key，连续按下回车即可      
```
git -keygen -t rsa -C "yourname@xxx.com"
```
生成成功后在你的用户目录.ssh目录下会有两个文件，如我的 C:\Users\myname\.ssh 下有 id_rsa 和 id_rsa.pub   

2. 以gitlab为例，使用你的账号登录gitlab，打开User Settings -> SSh Keys菜单，将1种的id_rsa.pub内容拷贝到Key输入框，title随便填，Add Key添加   
![image](https://github.com/jmilktea/jtea/blob/master/%E5%B7%A5%E5%85%B7%E7%B1%BB/git/images/git-ssh1.png)    

3. 验证，输入如下命令验证，如成功会有Welcome to...的输出    
```
ssh -T git@gitlabhost.com
```

4. 克隆代码   
```
git clone git@reposity.git
```

如果第三不失败，可以使用命令查看详细输出   
```
ssh -vT git@gitlabhost.com
```

如我的就失败了，输出原因是：“no mutual signature algorithm”，意思是高版本的git不支持rsa算法了，可以改用ed25519。    
第一步改成    
```
ssh-keygen -t ed25519 -C "yourname@xxx.com"
```
成功后会有id_ed25519，id_ed25519.pub两个文件，同样把id_ed25519.pub内容拷贝到gitlab创建一个key即可。    

**配置第二个仓库**    
和上面完成一样的流程，在第一步生成key时，我们指定一下生成文件名称，避免重名，如：C:\Users\myname\.ssh\id_ed25519_github     
由于有多个平台查库，所以我们需要告诉git哪个对应哪个key，在.ssh目录下新建一个config文件，不需要后缀，配置如下内容    
```
 Host gitlab.com
 HostName gitlab.com
 PreferredAuthentications publickey                                           
 IdentityFile ~/.ssh/id_ed25519                                            
                                                                                 
 Host github.com
 HostName github.com
 PreferredAuthentications publickey                                           
 IdentityFile ~/.ssh/id_ed25519_github   
``` 
host和hostname对应托管平台的地址。同理使用ssh -T git@github.com 验证第二个配置。    

如果我们使用idea，菜单Git -> Manage Remotes 输入不同仓库的远端地址就可以在同一个工程下，管理多个平台仓库了。      
![image](https://github.com/jmilktea/jtea/blob/master/%E5%B7%A5%E5%85%B7%E7%B1%BB/git/images/git-ssh2.png)   



