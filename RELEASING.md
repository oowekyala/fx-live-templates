

Just do
```shell
mvn release:clean release:prepare release:perform
```

Be sure that your settings.xml has credentials for an "ossrh" server:
```xml
<servers>
    <server>
        <id>ossrh</id>
        <username>TODO</username>
        <password>TODO</password>
    </server>
</servers>
```