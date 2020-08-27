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
