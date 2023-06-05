package com.lambdatest.tunnel;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.net.URL;
import java.util.HashMap;

public class LambdaTestTunnelTest {
	Tunnel t;

	WebDriver driver = null;
	public static String status = "passed";

	String username = System.getenv("LT_USERNAME");
	String access_key = System.getenv("LT_ACCESS_KEY");

	@BeforeTest
	public void setUp() throws Exception {

		DesiredCapabilities capabilities = new DesiredCapabilities();
		capabilities.setCapability("build", "Single Maven Tunnel");
		capabilities.setCapability("name", "New Maven Tunnel 1");
		capabilities.setCapability("platform", "Windows 10");
		capabilities.setCapability("browserName", "Chrome");
		capabilities.setCapability("version","latest");
		capabilities.setCapability("tunnel",true);
		capabilities.setCapability("network",true);
		capabilities.setCapability("console",true);
		capabilities.setCapability("visual",true);

		//create tunnel instance
		t = new Tunnel();
		HashMap<String, String> options = new HashMap<String, String>();
		options.put("user", username);
		options.put("key", access_key);

		//start tunnel
		t.start(options);
		driver = new RemoteWebDriver(new URL("http://" + username + ":" + access_key + "@hub.lambdatest.com/wd/hub"), capabilities);
		System.out.println("Started session");
	}

	@Test()
	public void testTunnel() throws Exception {
		//Check LocalHost on XAMPP
		driver.get("http://localhost.lambdatest.com");
		// Let's check that the item we added is added in the list.
		driver.get("https://google.com");
	}

	@AfterTest
	public void tearDown() throws Exception {
		((JavascriptExecutor) driver).executeScript("lambda-status=" + status);
		driver.quit();
		//close tunnel
		t.stop();
	}
}