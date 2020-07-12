package com.lambdatest.tunnel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertTrue;

public class LambdaTestTunnelTest {
	private Tunnel t;
	private Map<String, String> options;

	@Before
	public void setUp() throws Exception {
		t = new Tunnel();
		options = new HashMap<String, String>();
		options.put("user", System.getenv("LT_USERNAME"));
		options.put("key", System.getenv("LT_ACCESS_KEY"));
	}

	@Test
	public void testTunnelRunning() throws Exception {
		t.start(options);
		assertTrue(t.command.contains("-user"));
		assertTrue(t.command.contains(System.getenv("LT_USERNAME")));
		assertTrue(t.command.contains("-key"));
		assertTrue(t.command.contains(System.getenv("LT_ACCESS_KEY")));
	}

	@Test
	public void testMultipleTunnels() throws Exception {
		t.start(options);
		assertTrue(t.command.contains("-user"));
		assertTrue(t.command.contains(System.getenv("LT_USERNAME")));
		assertTrue(t.command.contains("-key"));
		assertTrue(t.command.contains(System.getenv("LT_ACCESS_KEY")));
		Tunnel t2 = new Tunnel();
		options.put("infoAPIPort","5010");
		t2.start(options);
		assertTrue(t2.command.contains("-user"));
		assertTrue(t2.command.contains(System.getenv("LT_USERNAME")));
		assertTrue(t2.command.contains("-key"));
		assertTrue(t2.command.contains(System.getenv("LT_ACCESS_KEY")));
	}

	@Test
	public void testSetTunnelName() throws Exception {
		options.put("tunnelName", "Arpit");
		t.start(options);
		assertTrue(t.command.contains("Arpit"));
	}

	@Test
	public void testServerAddress() throws Exception {
		options.put("server", "ltuns.lambdatest.com");
		t.start(options);
		assertTrue(t.command.contains("ltuns.lambdatest.com"));
	}

	@After
	public void tearDown() throws Exception {
		t.stop();
	}
}
