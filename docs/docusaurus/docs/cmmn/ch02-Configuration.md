---
id: ch02-Configuration
title: Configuration
---

## Creating a CmmnEngine

The Flowable CMMN engine is configured through an XML file called flowable.cmmn.cfg.xml. Note that this is **not** applicable if you’re using [the Spring style of building a process engine](cmmn/ch04-Spring.md#spring-integration).

The easiest way to obtain a CmmnEngine is to use the org.flowable.cmmn.engine.CmmnEngineConfiguration class:

    CmmnEngine cmmnEngine = CmmnEngineConfiguration.createStandaloneCmmnEngineConfiguration();

This will look for a flowable.cmmn.cfg.xml file on the classpath and construct an engine based on the configuration in that file. The following snippet shows an example configuration. The following sections will give a detailed overview of the configuration properties.

    <beans xmlns="http://www.springframework.org/schema/beans"
           xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
           xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans.xsd">

      <bean id="cmmnEngineConfiguration" class="org.flowable.cmmn.engine.CmmnEngineConfiguration">

        <property name="jdbcUrl" value="jdbc:h2:mem:flowable;DB_CLOSE_DELAY=1000" />
        <property name="jdbcDriver" value="org.h2.Driver" />
        <property name="jdbcUsername" value="sa" />
        <property name="jdbcPassword" value="" />

        <property name="databaseSchemaUpdate" value="true" />
      </bean>

    </beans>

Note that the configuration XML is in fact a Spring configuration. **This does not mean that Flowable can only be used in a Spring environment!** We are simply leveraging the parsing and dependency injection capabilities of Spring internally for building up the engine.

The CmmnEngineConfiguration object can also be created programmatically using the configuration file. It is also possible to use a different bean id (for example, see line 3).

    CmmnEngineConfiguration.
      createCmmnEngineConfigurationFromResourceDefault();
      createCmmnEngineConfigurationFromResource(String resource);
      createCmmnEngineConfigurationFromResource(String resource, String beanName);
      createCmmnEngineConfigurationFromInputStream(InputStream inputStream);
      createCmmnEngineConfigurationFromInputStream(InputStream inputStream, String beanName);

It is also possible not to use a configuration file, and create a configuration based on
defaults (see [the different supported classes](#configurationClasses) for more information).

    CmmnEngineConfiguration.createStandaloneCmmnEngineConfiguration();
    CmmnEngineConfiguration.createStandaloneInMemCmmnEngineConfiguration();

All these CmmnEngineConfiguration.createXXX() methods return a CmmnEngineConfiguration that can be tweaked further if needed. After calling the buildCmmnEngine() operation, a CmmnEngine is created:

    CmmnEngine cmmnEngine = CmmnEngineConfiguration.createStandaloneInMemCmmnEngineConfiguration()
      .setDatabaseSchemaUpdate(CmmnEngineConfiguration.DB_SCHEMA_UPDATE_TRUE)
      .setJdbcUrl("jdbc:h2:mem:my-own-db;DB_CLOSE_DELAY=1000")
      .buildCmmnEngine();

## CmmnEngineConfiguration bean

The flowable.cmmn.cfg.xml must contain a bean that has the id 'cmmnEngineConfiguration'.

     <bean id="cmmnEngineConfiguration" class="org.flowable.cmmn.engine.CmmnEngineConfiguration">

This bean is then used to construct the CmmnEngine. There are multiple classes available that can be used to define the cmmnEngineConfiguration. These classes represent different environments, and set defaults accordingly. It is a best practice to select the class that best matches your environment, to minimize the number of properties needed to configure the engine. The following classes are currently available:
<a name="configurationClasses"></a>

-   **org.flowable.cmmn.engine.impl.cfg.StandaloneInMemCmmnEngineConfiguration**: this is a convenience class for unit testing purposes. Flowable will take care of all transactions. An H2 in-memory database is used by default. The database will be created and dropped when the engine boots and shuts down. When using this, no additional configuration is probably needed.

-   **org.flowable.cmmn.spring.SpringCmmnEngineConfiguration**: To be used when the CMMN engine is used in a Spring environment. See [the Spring integration section](cmmn/ch04-Spring.md#spring-integration) for more information.

## Database configuration

There are two ways to configure the database that the Flowable CMMN engine will use. The first option is to define the JDBC properties of the database:

-   **jdbcUrl**: JDBC URL of the database.

-   **jdbcDriver**: implementation of the driver for the specific database type.

-   **jdbcUsername**: username to connect to the database.

-   **jdbcPassword**: password to connect to the database.

The data source that is constructed based on the provided JDBC properties will have the default [MyBatis](http://www.mybatis.org/) connection pool settings. The following attributes can optionally be set to tweak that connection pool (taken from the MyBatis documentation):

-   **jdbcMaxActiveConnections**: The number of active (i.e., in use) connections that the exist at any given time. Default is 10.

-   **jdbcMaxIdleConnections**: The number of idle connections that the exist at any given time.

-   **jdbcMaxCheckoutTime**: The amount of time in milliseconds that a connection can be 'checked out' of the connection pool before it is forcefully returned. Default is 20000. (20 seconds).

-   **jdbcMaxWaitTime**: This is a low level setting that gives the pool a chance to print a log status and re-attempt the acquisition of a connection in the case that it is taking unusually long (to avoid failing silently forever if the pool is misconfigured). Default is 20000. (20 seconds).

Example database configuration:

    <property name="jdbcUrl" value="jdbc:h2:mem:flowable;DB_CLOSE_DELAY=1000" />
    <property name="jdbcDriver" value="org.h2.Driver" />
    <property name="jdbcUsername" value="sa" />
    <property name="jdbcPassword" value="" />

Our benchmarks have shown that the MyBatis connection pool is not the most efficient or resilient when dealing with a lot of concurrent requests. As such, it is advised to use a javax.sql.DataSource implementation and inject it into the process engine configuration (For example HikariCP, Tomcat JDBC Connection Pool, etc.):

    <bean id="dataSource" class="org.apache.commons.dbcp.BasicDataSource" >
      <property name="driverClassName" value="com.mysql.jdbc.Driver" />
      <property name="url" value="jdbc:mysql://localhost:3306/flowable" />
      <property name="username" value="flowable" />
      <property name="password" value="flowable" />
      <property name="defaultAutoCommit" value="false" />
    </bean>

    <bean id="cmmnEngineConfiguration" class="org.flowable.cmmn.engine.CmmnEngineConfiguration">

      <property name="dataSource" ref="dataSource" />
      ...

Note that Flowable does not ship with a library that allows you to define such a data source. So you need to make sure that the libraries are on your classpath.

The following properties can be set, regardless of whether you are using the JDBC or data source approach:

-   **databaseType**: it’s normally not necessary to specify this property, as it is automatically detected from the database connection metadata. Should only be specified when automatic detection fails. Possible values: {h2, mysql, oracle, postgres, mssql, db2}. This setting will determine which create/drop scripts and queries will be used. See [the 'supported databases' section](cmmn/ch02-Configuration.md#supported-databases) for an overview of which types are supported.

-   **databaseSchemaUpdate**: sets the strategy to handle the database schema on process engine boot and shutdown.

    -   false (default): Checks the version of the DB schema against the library when the process engine is being created and throws an exception if the versions don’t match.

    -   true: Upon building the process engine, a check is performed and an update of the schema is performed if it is necessary. If the schema doesn’t exist, it is created.

    -   create-drop: Creates the schema when the process engine is being created and drops the schema when the process engine is being closed.

## JNDI Datasource Configuration

By default, the database configuration for Flowable is contained within the db.properties files in the WEB-INF/classes of each web application. This isn’t always ideal because it
requires users to either modify the db.properties in the Flowable source and recompile the WAR file, or explode the WAR and modify the db.properties on every deployment.

By using JNDI (Java Naming and Directory Interface) to obtain the database connection, the connection is fully managed by the Servlet Container and the configuration can be managed outside the WAR deployment. This also allows more control over the connection parameters than what is provided by the db.properties file.

### Configuration

Configuration of the JNDI data source will differ depending on what servlet container application you are using. The instructions below will work for Tomcat, but for other container applications, please refer to the documentation for your container app.

If using Tomcat, the JNDI resource is configured within $CATALINA\_BASE/conf/\[enginename\]/\[hostname\]/\[warname\].xml (for the Flowable UI this will usually be $CATALINA\_BASE/conf/Catalina/localhost/flowable-app.xml). The default context is copied from the Flowable WAR file when the application is first deployed, so if it already exists, you will need to replace it. To change the JNDI resource so that the application connects to MySQL instead of H2, for example, change the file to the following:

    <?xml version="1.0" encoding="UTF-8"?>
        <Context antiJARLocking="true" path="/flowable-app">
            <Resource auth="Container"
                name="jdbc/flowableDB"
                type="javax.sql.DataSource"
                description="JDBC DataSource"
                url="jdbc:mysql://localhost:3306/flowable"
                driverClassName="com.mysql.jdbc.Driver"
                username="sa"
                password=""
                defaultAutoCommit="false"
                initialSize="5"
                maxWait="5000"
                maxActive="120"
                maxIdle="5"/>
            </Context>

### JNDI properties

To configure a JNDI data source, use the following properties in the properties file for the Flowable UI:

-   spring.datasource.jndi-name=: the JNDI name of the data source.

-   datasource.jndi.resourceRef: Set whether the lookup occurs in a J2EE container, for example, the prefix "java:comp/env/" needs to be added if the JNDI name doesn’t already contain it. Default is "true".

### Custom properties

System properties can also be used in the flowable.cmmn.cfg.xml by using them in the format `${propertyName:defaultValue}`.

    <property name="jdbcUrl" value="${jdbc.url:jdbc:h2:mem:flowable;DB_CLOSE_DELAY=1000}" />
    <property name="jdbcDriver" value="${jdbc.driver:org.h2.Driver}" />
    <property name="jdbcUsername" value="${jdbc.username:sa}" />
    <property name="jdbcPassword" value="${jdbc.password:}" />

Using this configuration if the property `jdbc.url` is available then it would be used for the `jdbcUrl` of the `CmmnEngineConfiguration`.
Otherwise the value after the first `:` would be used.

It is also possible to define locations from where properties can be picked up from the system by using a bean of type org.springframework.beans.factory.config.PropertyPlaceholderConfigurer.

Example configuration with custom location for properties

    <beans xmlns="http://www.springframework.org/schema/beans"
           xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
           xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans.xsd">

      <bean class="org.springframework.beans.factory.config.PropertyPlaceholderConfigurer">
        <property name="location" value="file:/opt/conf/flowable.properties" />
      </bean>

      <bean id="cmmnEngineConfiguration" class="org.flowable.cmmn.engine.CmmnEngineConfiguration">

        <property name="jdbcUrl" value="${jdbc.url:jdbc:h2:mem:flowable;DB_CLOSE_DELAY=1000}" />
        <property name="jdbcDriver" value="${jdbc.driver:org.h2.Driver}" />
        <property name="jdbcUsername" value="${jdbc.username:sa}" />
        <property name="jdbcPassword" value="${jdbc.password:}" />

        <property name="databaseSchemaUpdate" value="true" />
      </bean>

    </beans>

With this configuration the properties would be first looked up in the /opt/conf/flowable.properties file.

## Supported databases

Listed below are the types (case sensitive!) that Flowable uses to refer to databases.

<table>
<colgroup>
<col style="width: 33%" />
<col style="width: 33%" />
<col style="width: 33%" />
</colgroup>
<thead>
<tr class="header">
<th>Flowable database type</th>
<th>Example JDBC URL</th>
<th>Notes</th>
</tr>
</thead>
<tbody>
<tr class="odd">
<td><p>h2</p></td>
<td><p>jdbc:h2:tcp://localhost/flowable</p></td>
<td><p>Default configured database</p></td>
</tr>
<tr class="even">
<td><p>mysql</p></td>
<td><p>jdbc:mysql://localhost:3306/flowable?autoReconnect=true</p></td>
<td><p>Tested using mysql-connector-java database driver</p></td>
</tr>
<tr class="odd">
<td><p>oracle</p></td>
<td><p>jdbc:oracle:thin:@localhost:1521:xe</p></td>
<td></td>
</tr>
<tr class="even">
<td><p>postgres</p></td>
<td><p>jdbc:postgresql://localhost:5432/flowable</p></td>
<td></td>
</tr>
<tr class="odd">
<td><p>db2</p></td>
<td><p>jdbc:db2://localhost:50000/flowable</p></td>
<td></td>
</tr>
<tr class="even">
<td><p>mssql</p></td>
<td><p>jdbc:sqlserver://localhost:1433;databaseName=flowable (jdbc.driver=com.microsoft.sqlserver.jdbc.SQLServerDriver) <em>OR</em> jdbc:jtds:sqlserver://localhost:1433/flowable (jdbc.driver=net.sourceforge.jtds.jdbc.Driver)</p></td>
<td><p>Tested using Microsoft JDBC Driver 4.0 (sqljdbc4.jar) and JTDS Driver</p></td>
</tr>
</tbody>
</table>

## Creating the database tables

The easiest way to create the database tables for your database is to:

-   Add the flowable-cmmn-engine JARs to your classpath

-   Add a suitable database driver

-   Add a Flowable configuration file (*flowable.cmmn.cfg.xml*) to your classpath, pointing to your database (see [database configuration section](cmmn/ch02-Configuration.md#database-configuration))

-   Execute the main method of the *DbSchemaCreate* class

However, often only database administrators can execute DDL statements on a database. On a production system, this is also the wisest of choices. The SQL DDL statements can be found on the Flowable downloads page or inside the Flowable distribution folder, in the database subdirectory. The scripts are also in the engine JAR (*flowable-cmmn-engine-x.jar*), in the package *org/flowable/cmmn/db/create*. The SQL files are of the form

    flowable.{db}.cmmn.create.sql

Where *db* is any of the [supported databases](cmmn/ch02-Configuration.md#supported-databases).

## Database table names explained

The database names of the Flowable CMMN Engine all start with **ACT\_CMMN\_**. The second part is a two-character identification of the use case of the table. This use case will also roughly match the service API.

-   **ACT\_CMMN\_**\*: Tables without an additional prefix contain 'static' information such as case definitions and case resources (images, rules, etc.).

-   **ACT\_CMMN\_RU\_**\*: 'RU' stands for runtime. These are the runtime tables that contain the runtime data of case instances, plan items, and so on. Flowable only stores the runtime data during case instance execution and removes the records when a case instance ends. This keeps the runtime tables small and fast.

-   **ACT\_CMMN\_HI\_**\*: 'HI' stands for history. These are the tables that contain historical data, such as past case instances, plan items, and so on.

## Database upgrade

Make sure you make a backup of your database (using your database backup capabilities) before you run an upgrade.

By default, a version check will be performed each time a process engine is created. This typically happens once at boot time of your application or of the Flowable webapps. If the Flowable library notices a difference between the library version and the version of the Flowable database tables, then an exception is thrown.

To upgrade, you have to start by putting the following configuration property in your flowable.cmmn.cfg.xml configuration file:

    <beans >

      <bean id="cmmnEngineConfiguration"
          class="org.flowable.cmmn.engine.CmmnEngineConfiguration">
        <!-- ... -->
        <property name="databaseSchemaUpdate" value="true" />
        <!-- ... -->
      </bean>

    </beans>

**Also, include a suitable database driver for your database to the classpath.** Upgrade the Flowable libraries in your application. Or start up a new version of Flowable and point it to a database that contains data from an older version. With databaseSchemaUpdate set to true, Flowable will automatically upgrade the DB schema to the newest version the first time when it notices that libraries and DB schema are out of sync.

**As an alternative, you can also run the upgrade DDL statements.** It’s also possible to run the upgrade database scripts available on the Flowable downloads page.

## History configuration

Customizing the configuration of history storage is optional. This allows you to tweak settings that influence the [history capabilities](#history) of the engine. See [history configuration](#historyConfig) for more details.

    <property name="history" value="audit" />

## Exposing configuration beans in expressions and scripts

By default, all beans that you specify in the flowable.cmmn.cfg.xml configuration or in your own Spring configuration file are available to expressions and scripts. If you want to limit the visibility of beans in your configuration file, you can configure a property called beans in your process engine configuration. The beans property in CmmnEngineConfiguration is a map. When you specify that property, only beans specified in that map will be visible to expressions and scripts. The exposed beans will be exposed with the names as you specify in the map.

## Deployment cache configuration

All case definitions are cached (after they’re parsed) to avoid hitting the database every time a case definition is needed and because case definition data doesn’t change. By default, there is no limit on this cache. To limit the case definition cache, add the following property:

    <property name="caseDefinitionCacheLimit" value="10" />

Setting this property will swap the default hashmap cache with a LRU cache that has the provided hard limit. Of course, the 'best' value for this property depends on the total amount of case definitions stored and the number of case definitions actually used at runtime by all the runtime case instances.

You can also inject your own cache implementation. This must be a bean that implements the org.flowable.engine.common.impl.persistence.deploy.DeploymentCache interface:

    <property name="caseDefinitionCache">
      <bean class="org.flowable.MyCache" />
    </property>

## Logging

All logging (flowable, spring, mybatis, …​) is routed through SLF4J and allows for selecting the logging-implementation of your choice.

**By default no SFL4J-binding JAR is present in the flowable-cmmn-engine dependencies, this should be added in your project in order to use the logging framework of your choice.** If no implementation JAR is added, SLF4J will use a NOP-logger, not logging anything at all, other than a warning that nothing will be logged. For more info on these bindings [<http://www.slf4j.org/codes.html#StaticLoggerBinder>](http://www.slf4j.org/codes.html#StaticLoggerBinder).

With Maven, add for example a dependency like this (here using log4j), note that you still need to add a version:

    <dependency>
      <groupId>org.slf4j</groupId>
      <artifactId>slf4j-reload4j</artifactId>
    </dependency>

The flowable-rest webapp is configured to use Log4j-binding. Log4j is also used when running the tests for all the flowable-\* modules.

**Important note when using a container with commons-logging in the classpath:** In order to route the spring-logging through SLF4J, a bridge is used (see [<http://www.slf4j.org/legacy.html#jclOverSLF4J>](http://www.slf4j.org/legacy.html#jclOverSLF4J)). If your container provides a commons-logging implementation, please follow directions on this page: [<http://www.slf4j.org/codes.html#release>](http://www.slf4j.org/codes.html#release) to ensure stability.

Example when using Maven (version omitted):

    <dependency>
      <groupId>org.slf4j</groupId>
      <artifactId>jcl-over-slf4j</artifactId>
    </dependency>
