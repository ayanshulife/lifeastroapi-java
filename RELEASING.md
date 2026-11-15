# Releasing to Maven Central

The repository owner publishes. Three artifacts go up together, all under `com.lifeastroapi`:
`lifeastroapi-parent` (the parent POM), `lifeastroapi-java` and `lifeastroapi-spring-boot-starter`.

## One-time setup

1. **Central Portal account.** Sign in at https://central.sonatype.com (GitHub or email login).

2. **Verify the namespace `com.lifeastroapi`.** In the portal: *Namespaces* → *Add Namespace* →
   `com.lifeastroapi`. The portal shows a verification key. Add it as a DNS **TXT** record on the
   domain `lifeastroapi.com` (host `@`, value = the key), wait until it resolves
   (`dig +short TXT lifeastroapi.com`), then click *Verify Namespace*. The TXT record can be
   removed after the namespace shows *Verified*.

3. **Publishing token.** In the portal: *View Account* → *Generate User Token*. Put the token's
   username and password into `~/.m2/settings.xml` (not into this repository):

   ```xml
   <settings>
     <servers>
       <server>
         <id>central</id>
         <username>TOKEN-USERNAME</username>
         <password>TOKEN-PASSWORD</password>
       </server>
     </servers>
   </settings>
   ```

   The id `central` is the `publishingServerId` in the root `pom.xml`.

4. **GPG key.** Central needs every file signed. `gpg` is already installed (Homebrew).

   ```bash
   gpg --full-generate-key                      # RSA 4096 (or ed25519), name "Ayanshu Life", support@lifeastroapi.com
   gpg --list-secret-keys --keyid-format=long   # note the key id
   gpg --keyserver keyserver.ubuntu.com --send-keys <KEY-ID>
   gpg --keyserver keys.openpgp.org --send-keys <KEY-ID>
   ```

   Keep a backup of the key and its passphrase; later releases must be signed with a key Central
   can find on a key server.

## Each release

1. Set the version in the four POMs (`pom.xml`, `lifeastroapi-java/pom.xml`,
   `lifeastroapi-spring-boot-starter/pom.xml` parent versions) and in `LifeAstro.VERSION`
   (`ConfigurationTest` checks they match), and add the CHANGELOG entry.
2. Check everything: `./mvnw clean verify`.
3. Upload:

   ```bash
   ./mvnw -Prelease clean deploy
   ```

   This builds the jars, the sources and Javadoc jars, signs every file (gpg asks for the
   passphrase), and uploads one bundle to the portal. It waits until Central has validated it.
4. In the portal, under *Deployments*, check the bundle (three components, status *Validated*) and
   click **Publish**. (`autoPublish` is off, so nothing goes public without this click.) The
   artifacts are on Maven Central in about 10–30 minutes; search.maven.org can take longer.
5. Commit, tag `v<version>` and push with GitHub Desktop; make the GitHub release from the tag.
6. Check from a clean project that `com.lifeastroapi:lifeastroapi-java:<version>` resolves.
