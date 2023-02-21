package com.lambdatest.tunnel;

import org.testng.TestNG;
import org.testng.TestListenerAdapter;

public class TunnelTestRunner {
    private static TestListenerAdapter listener;
    public static void main(String[] args){
        listener = new TestListenerAdapter();
        TestNG testng = new TestNG();
        testng.setTestClasses(new Class[] {LambdaTestTunnelTest.class});
        testng.addListener(listener);
        testng.run();
        
        int passedTests = listener.getPassedTests().size();
        int failedTests = listener.getFailedTests().size();
        
        System.out.println("Number of passed tests: " + passedTests);
        System.out.println("Number of failed tests: " + failedTests);
        
    }
}
