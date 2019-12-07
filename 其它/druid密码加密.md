## 前言
对于正式环境，dba和运维都不希望把账号密码配置到配置文件，这样就算是小白拿到文件都能轻松访问数据库，容易出现安全问题。本章介绍如何使用druid对密码进行加密。

## 实现
1. 找到druid的jar包，执行java -cp druid-1.1.20.jar com.alibaba.druid.filter.config.ConfigTools passwrod，则会生成一些信息，如下  
![image](https://github.com/jmilktea/jmilktea/blob/master/%E5%85%B6%E5%AE%83/images/druid-pwd.png)

2. 接着在应用配置文件做如下配置
```
url: jdbc:mysql://${MASTER_DB_IP}:${MASTER_DB_PORT}/akuocean?useUnicode=true&characterEncoding=utf-8&serverTimezone=UTC
username: ${MASTER_DB_USERNAME}
password: ${MASTER_DB_PASSWORD}
driver-class-name: com.mysql.jdbc.Driver
name: master
filters: config
connectProperties: {config.decrypt: true, config.decrypt.key: ${publickey}}
```
其中filters需要指定config，druid通过它来对加密密码进行解密。connectProperties的config.decrypt: true表示已经进行加密，publickey为上面生成的key，这样password即可配置为加密后的字符串。

## 思考
1. connectProperties配置  
需要如上面那样配置，这里容易出错，详细见：[issure](https://github.com/alibaba/druid/issues/2302)

2. druid的加密意义何在？  
虽然我们配置了加密后的文本，但如果我拿到了加密后的password和publickey，依然能在本地轻松获得密码，如此来说该加密是否还有意义？实际上访问数据库一定要账号密码，而且是明文，这点是数据库的规定，druid在这层上也是尽力了。一般我们都不会把安全信息写在配置文件上（经历过的公司基本都会出现开发将代码上传到github），而是通过环境变量传递，这样我们可以在执行脚本上再做一层安全控制。
