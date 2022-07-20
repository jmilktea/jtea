## tag    
tag是一个版本的标记，方便我们对软件进行版本跟踪和管理。  
当我们在发版前，会对程序打上一个tag，标记版本。然后再把修改的分支合并过来，如果发现合并后有问题，可以快速回滚到指定的tag  

**命令**    
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

## stash     
git stash用于保存和恢复工作进度。当你切换到某个开发分支开发到一半时，突然有个事需要切换到另一个分支，这个时候代码还是个半成品，可以使用git stash暂存当前的修改，过后可以回到当前的stash继续修改。
与commit的区别？git stash是一种可以切换分支的not commit状态。commit应该表示工作已经基本完成，可以work，是一次比较完整的提交。而stash还处于工作的过程中，属于半成品，需要继续开发。两者的目的不同。

**命令**    
- git stash [save message]，save message是备注，可选
- git stash list，显示所有stash
- git stash pop，恢复最新的进度到工作区
- git stash apply stash@{0}，恢复到某个stash到工作区

**操作**    
在idea里可以直接使用上面的命令在Terminal命令行操作。也可以使用图形化操作。  
选择git stash后可以写message，然后发现本地修改已经被暂存，恢复到原来的状态，可以切换分支  
![image](https://github.com/jmilktea/jmilktea/blob/master/%E5%B7%A5%E5%85%B7%E7%B1%BB/git/images/git-stash.png)  

选择git unstash后可以选择恢复到原来的修改，也可以通过view查看本次暂存了哪些文件  
![image](https://github.com/jmilktea/jmilktea/blob/master/%E5%B7%A5%E5%85%B7%E7%B1%BB/git/images/git-unstash.png)    


## ff/nf/squash     
这里要讲的是git merge几个参数，-ff(fast forward)，-nf (no fast forward)，--squash。在使用git工具在进行代码合并的时候，我们经常会看到它们，例如 tortoisegit 或者 idea，如图  
![image](https://github.com/jmilktea/jmilktea/blob/master/%E5%B7%A5%E5%85%B7%E7%B1%BB/git/images/tor-merge-param.png)
![image](https://github.com/jmilktea/jmilktea/blob/master/%E5%B7%A5%E5%85%B7%E7%B1%BB/git/images/idea-merge-param.png)
 
这里我们创建test_ff，test_nf, test_squash 分支，并且每个分支commit几次修改，来测试各个参数，然后合并到master，观察merge log。
- ff(fast forward)  
快进方式，使用该方式的特点是分支的提交历史都会合并过来，并且分支的修改看起来就是在master分支修改一样，如图：
![image](https://github.com/jmilktea/jmilktea/blob/master/%E5%B7%A5%E5%85%B7%E7%B1%BB/git/images/git-ff.png)
 
- nf(no fast forward)  
关闭ff模式，使用该方式同样会把提交历史合并过来，不过它会以一个分支的形式合并过来，不会像直接在master修改，如图：
![image](https://github.com/jmilktea/jmilktea/blob/master/%E5%B7%A5%E5%85%B7%E7%B1%BB/git/images/git-nf.png)

- squash(压缩)  
有时候我们的分支开发一个功能可能会有很多次的commit，如果把这些commit history都合并过来，master的commit历史看起来会非常多和混乱，squash 是压缩的意思，可以把分支的commit log压缩成一次，squash合并后需要重新commit一次，如图：
![image](https://github.com/jmilktea/jmilktea/blob/master/%E5%B7%A5%E5%85%B7%E7%B1%BB/git/images/git-squash.png)

- 对比图  
![images](https://github.com/jmilktea/jmilktea/blob/master/%E5%B7%A5%E5%85%B7%E7%B1%BB/git/images/git-ffnfsquash.png)