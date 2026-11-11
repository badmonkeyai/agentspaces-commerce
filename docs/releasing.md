# Releasing

Written for: maintainers who publish agentspaces-commerce to Maven Central.

The `central-publish` profile attaches the sources and javadoc jars, signs every artifact with GPG (the passphrase from `MAVEN_GPG_PASSPHRASE`, or a GPG agent), and uploads through the Sonatype Central Portal using the `central` server id in `settings.xml`.

## Order

Publish the AgentSpaces core 0.6.0 first, and wait until its BOM and `agentspaces-api` are live on `repo1`:

```sh
curl -sfI https://repo1.maven.org/maven2/ai/badmonkey/agentspaces/agentspaces-dependencies/0.6.0/agentspaces-dependencies-0.6.0.pom
curl -sfI https://repo1.maven.org/maven2/ai/badmonkey/agentspaces/agentspaces-api/0.6.0/agentspaces-api-0.6.0.pom
```

This library's published POM imports that BOM and names `agentspaces-api`, so Central refuses the bundle while the core is not live. No other AgentSpaces library is needed first.

## The dry run

It builds the jars without signing or uploading anything:

```sh
mvn -Pcentral-publish -DskipTests -Dgpg.skip=true clean package
```

The jars under `target/` are the library, its sources, and its javadoc.

## The release

It signs the artifacts and uploads them:

```sh
mvn -Pcentral-publish clean deploy
```

`autoPublish` is off, so the upload waits on the Portal as a validated bundle until you publish it there. Then tag the release commit with the bare version (`0.6.0`) and push the tag. Central never replaces a published version, so the next version fixes a bad release.

CI runs the same profile unsigned on every push, so a javadoc or packaging problem fails a pull request before it can fail a release.
