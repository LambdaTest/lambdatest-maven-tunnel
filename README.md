# LambdaTest Maven Tunnel
![LambdaTest Logo](https://www.lambdatest.com/static/images/logo.svg)

---

### Prerequisites
1. Maven is required to be installed:
   https://maven.apache.org/install.html

### Environment Setup
1. Global Dependencies
    * Install [Java8](https://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html)
2. Lambdatest Credentials
    * Set LambdaTest username and access key in environment variables. It can be obtained from [LambdaTest dashboard](https://automation.lambdatest.com/)    
    example:
    - For linux/mac
    ```
    export LT_USERNAME="YOUR_USERNAME"
    export LT_ACCESS_KEY="YOUR ACCESS KEY"
    
    ```
    - For Windows
    ```
    set LT_USERNAME="YOUR_USERNAME"
    set LT_ACCESS_KEY="YOUR ACCESS KEY"
    
    ```
3. Add following dependency to your POM.xml file
```xml
<dependency>
    <groupId>com.github.lambdatest</groupId>
	  <artifactId>lambdatest-tunnel-binary</artifactId>
    <version>1.0.4</version>
</dependency>
```
## Example

```java
import com.lambdatest.tunnel.Tunnel;

# creates an instance of Tunnel
Tunnel t = new Tunnel();

# replace <lambdatest-username>,<lambdatest-accesskey> with your username and key. You can also set an environment variable - "LT_USERNAME" and "LT_ACCESS_KEY".
HashMap<String, String> tunnelArgs = new HashMap<String, String>();
tunnelArgs.put("user", "<lambdatest-username>");
tunnelArgs.put("key", "<lambdatest-accesskey>");

# starts the tunnel instance with the required arguments
t.start(tunnelArgs);

# stops the tunnel instance
t.stop();
```
## Arguments

Apart from the username and access key, all other lambdatest tunnel parameters are optional.

#### Change Tunnel Name
```java
tunnelArgs.put("tunnelName","YourName");
```
#### Change pid path
```java
tunnelArgs.put("pidFile","Your/pid/path");
```
#### Change directory path
```java
tunnelArgs.put("dir","give/lambda/directory/path");
```
#### Change tunnel.log path
```java
tunnelArgs.put("logFile","give/tunnel/log/directory/path");
```
For full list of tunnel parameters, please refer tunnel parameters documentation file.

### Advice/Troubleshooting
1. It may be useful to use a Java IDE such as IntelliJ or Eclipse to help troubleshoot potential issues. 

## About LambdaTest
[LambdaTest](https://www.lambdatest.com/) is a cloud based selenium grid infrastructure that can help you run automated cross browser compatibility tests on 2000+ different browser and operating system environments. LambdaTest supports all programming languages and frameworks that are supported with Selenium, and have easy integrations with all popular CI/CD platforms. It's a perfect solution to bring your [selenium automation testing](https://www.lambdatest.com/selenium-automation) to cloud based infrastructure that not only helps you increase your test coverage over multiple desktop and mobile browsers, but also allows you to cut down your test execution time by running tests on parallel.
