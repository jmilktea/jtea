## 前言
tag是一个版本的标记，方便我们对软件进行版本跟踪和管理。  
当我们在发版前，会对程序打上一个tag，标记版本。然后再把修改的分支合并过来，如果发现合并后有问题，可以快速回滚到指定的tag  

## 命令
1. 查看所有tag  
```
git tag  
```
2. 打tag  
```
git tag v1.0  
```
3. 推送tag到远端      
```
git push --tags
```  
4. 切换分支修改文件，合并分支，推送代码  
```
git checkout -b dev_tag      
git commit -am 'tag'   
git checkout master    
git merge dev_tag  
git push origin master  
```
5. 回滚到tag v1.0  
```
 git reset --hard v1.0 
```
6. 推送，强制覆盖master
```
git push -f origin master  
```
