在常规的编程模式也就是命令式编程下，方法的调用关系简单，清晰，当程序出现异常，可以很容易定位到异常的位置，程序也会输出清晰的stack trace，我们可以很容易进行调试排查。但在响应式编程下，这变得困难，由于层层包装和调用链变得复杂，调试起来比较困难，stack trace的信息也变得不那么直观。  
我们看到如下代码，这是个简单的调用链，实际可能是很复杂的，并且代码不再一条调用链上。程序数字输出，然后*2，并且过滤掉取余2不为0的(*2后都不满足)，然后single取一个元素，并且输出。
```
        Flux.just(1, 2, 3, 4)
                .flatMap(s -> {
                    System.out.println("item:" + s);
                    return Mono.just(s * 2);
                })
                .filter(s -> s % 2 != 0)
                .single()
                .doOnNext(s -> System.out.println("single result is:" + s))
                .subscribe();
```
我们看运行结果
可以看到输出的信息，比较有用的Source was empty，有经验的可能会通过MonoSingle$SingleSubscriber这里去找到有没有single的相关调用，但是像上面说的，这不够直观，并且在调用链很长的情况下，或者有多个single调用时，这个定位变得困难。最后通过stack trace的输出，它指向的错误是subscribe这一行，也不是我们想要的结果。
- 启用调试模式
```
Hooks.onOperatorDebug();
```
可以看到除了输出source was empty，还帮我们定位到了具体的single方法。Hooks.onOperatorDebug会开启debug模式，全局生效，对每一个操作符都起作用，这对性能会有一定影响，生产环境一般不要开启。另外我们可以通过 -Dreactor.trace.operatorStacktrace=true开关来设置，效果是一样的。

- checkpoint
```
        Flux.just(1, 2, 3, 4)
                .flatMap(s -> {
                    System.out.println("item:" + s);
                    return Mono.just(s * 2);
                })
                .filter(s -> s % 2 != 0)
                .single()
                .doOnNext(s -> System.out.println("single result is:" + s))
                .checkpoint("debug")
                .subscribe();
```
如果我们只想知道某个调用链上的stack trace，也可以通过checkpoint来实现，它的作用域只在当前调用链上，通过标识符参数"debug"，可以在stack trace很多的情况下帮我们快速定位问题。

- log
```
        Flux.just(1, 2, 3, 4)
                .flatMap(s -> {
                    System.out.println("item:" + s);
                    return Mono.just(s * 2);
                })
                .filter(s -> s % 2 != 0)
                .single()
                .log("single debug")
                .doOnNext(s -> System.out.println("single result is:" + s))
                .subscribe();
```
log方法可以打印操作符的只想过程，如onNext,onError，onComplete等，辅助调试，它的作用域是在操作符级别。log默认是使用info级别输出日志，如果使用了SLF4J,会自动输出到对应文件，否则输出到控制台。

