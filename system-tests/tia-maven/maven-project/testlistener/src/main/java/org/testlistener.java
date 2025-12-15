import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.engine.TestExecutionResult;
public class testlistener implements TestExecutionListener {

	public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
		 System.out.println("THIS IS MY MESSAGE - Test execution finished");
	}
}