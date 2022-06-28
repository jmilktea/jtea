package agent;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;

/**
 * @author huangyb1
 * @date 2022/6/27
 */
public class AgentApplication {

	private static final String timeWatch = "com.jmilktea.sample.demo.bytebuddy.TimeWatch";

	public static void premain(String agentArgs, Instrumentation inst) {
		System.out.println("agent premain run");
		AgentBuilder.Transformer transformer = new AgentBuilder.Transformer() {
			public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, TypeDescription typeDescription, ClassLoader classLoader, JavaModule module) {
				return builder
						.method(ElementMatchers.<MethodDescription>isAnnotatedWith(ElementMatchers.named(timeWatch)))
						.intercept(MethodDelegation.to(TimeWatchInterceptor.class));
			}
		};

		new AgentBuilder.Default().type(ElementMatchers.<TypeDescription>nameStartsWith("com.jmilktea")).transform(transformer).installOn(inst);
	}

	public static class TimeWatchInterceptor {
		@RuntimeType
		public static Object intercept(@Origin Method method,
									   @SuperCall Callable<?> callable) throws Exception {
			long start = System.currentTimeMillis();
			try {
				return callable.call();
			} finally {
				System.out.println(method.getName() + " use:" + (System.currentTimeMillis() - start));
			}
		}
	}
}
