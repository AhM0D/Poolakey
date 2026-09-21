# Poolakey — multi-market fork

A fork of [cafebazaar/Poolakey](https://github.com/cafebazaar/Poolakey) that serves **both
Cafe Bazaar and Myket from a single artifact**. Upstream Poolakey hardcodes Bazaar's package
name, billing service action and signing certificate; this fork moves the market's identity
into manifest meta-data supplied by the consuming app, and derives the rest from it.

**The public Kotlin API is unchanged.** `Payment`, `PaymentConfiguration`, `SecurityCheck`
and `Connection` keep the signatures upstream has, so existing code — and any prebuilt Unity
bridge `.aar` — keeps working without recompilation. You select the market in gradle, not in
code.

---

## Quick start

### 1. Dependency

```groovy
repositories {
    google()
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation "com.github.AhM0D.Poolakey:poolakey:[latest_version]"
}
```

### 2. Declare which market you are shipping to

Add **three** manifest placeholders to the module that consumes Poolakey. Pick the block for
your store:

```groovy
// Cafe Bazaar
android {
    defaultConfig {
        manifestPlaceholders = [
                marketApplicationId: "com.farsitel.bazaar",
                marketBindAddress  : "ir.cafebazaar.pardakht.InAppBillingService.BIND",
                marketPermission   : "com.farsitel.bazaar.permission.PAY_THROUGH_BAZAAR"
        ]
    }
}
```

```groovy
// Myket
android {
    defaultConfig {
        manifestPlaceholders = [
                marketApplicationId: "ir.mservices.market",
                marketBindAddress  : "ir.mservices.market.InAppBillingService.BIND",
                marketPermission   : "ir.mservices.market.BILLING"
        ]
    }
}
```

All three are required — a missing one fails the manifest merge at build time, which is the
intended behaviour. There is no implicit default.

To ship both stores from one codebase, use product flavors:

```groovy
flavorDimensions "store"
productFlavors {
    bazaar {
        dimension "store"
        manifestPlaceholders = [ /* Bazaar block above */ ]
    }
    myket {
        dimension "store"
        manifestPlaceholders = [ /* Myket block above */ ]
    }
}
```

### 3. Use Poolakey exactly as upstream documents it

```kotlin
val securityCheck = SecurityCheck.Enable(rsaPublicKey = "YOUR_STORE_RSA_KEY")
val payment = Payment(PaymentConfiguration(securityCheck))
```

Nothing market-specific appears in your code. See the [upstream
wiki](https://github.com/cafebazaar/Poolakey/wiki) for the full API.

> The RSA public key is **per store**. Bazaar's comes from the Bazaar developer panel,
> Myket's from Myket's. Passing Bazaar's key to a Myket build will fail purchase
> verification.

---

## Unity

Unity has no product flavors — one build produces one variant — so the store is selected by a
scripting define plus a build-time gradle edit.

**1.** Declare the dependency for the External Dependency Manager, in any `*Dependencies.xml`
under an `Editor/` folder:

```xml
<dependencies>
  <androidPackages>
    <androidPackage spec="com.github.AhM0D.Poolakey:poolakey:[latest_version]">
      <repositories>
        <repository>https://jitpack.io</repository>
      </repositories>
    </androidPackage>
    <androidPackage spec="org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.10" packageOverrides="true" />
  </androidPackages>
</dependencies>
```

**2.** Put the placeholders in **`launcherTemplate.gradle`**, not `mainTemplate.gradle`.

This is the part that catches people out. `mainTemplate.gradle` is `apply plugin:
'com.android.library'` — it becomes the `unityLibrary` module, which is where EDM4U resolves
Poolakey's own dependency and manifest. But an Android **library** module does not substitute
manifest placeholders; it passes them through unresolved for whatever consumes it. Placeholder
substitution happens when manifests are *merged*, which occurs in the **application** module —
`launcherTemplate.gradle`, which is `apply plugin: 'com.android.application'` and becomes the
`launcher` module. Declare the placeholders there so they resolve into the manifest Poolakey's
library contributes.

Declaring the same block in `mainTemplate.gradle` too is harmless — a library module simply
carries it through unused — and is a reasonable safe default if you are unsure which module
will end up doing the merging.

```groovy
def store = project.properties['storeName'] ?: "BAZAAR"

android {
    defaultConfig {
        // ... Unity's generated entries ...

        if (store.equalsIgnoreCase("MYKET")) {
            manifestPlaceholders = [
                    marketApplicationId: "ir.mservices.market",
                    marketBindAddress  : "ir.mservices.market.InAppBillingService.BIND",
                    marketPermission   : "ir.mservices.market.BILLING"
            ]
        } else {
            manifestPlaceholders = [
                    marketApplicationId: "com.farsitel.bazaar",
                    marketBindAddress  : "ir.cafebazaar.pardakht.InAppBillingService.BIND",
                    marketPermission   : "com.farsitel.bazaar.permission.PAY_THROUGH_BAZAAR"
            ]
        }
    }
}
```

Add this to `launcherTemplate.gradle`'s `android { defaultConfig { ... } }` block (Unity's
generated file for the `com.android.application` module). Keeping an identical copy in
`mainTemplate.gradle` is optional but does no harm.

**3.** Write `storeName` from an editor build hook, keyed off your scripting define:

```csharp
public class StoreBuildProcessor : IPreprocessBuildWithReport
{
    public int callbackOrder => 0;

#if BP_MYKET
    public static string SelectedStore = "MYKET";
#else
    public static string SelectedStore = "BAZAAR";
#endif

    public void OnPreprocessBuild(BuildReport report)
    {
        if (report.summary.platform != BuildTarget.Android) return;
        // rewrite or append `storeName=<SelectedStore>` in
        // Assets/Plugins/Android/gradleTemplate.properties
    }
}
```

**4.** Verify the built APK before shipping. An unsubstituted placeholder is silent:

```bash
unzip -p your.apk AndroidManifest.xml \
  | python3 -c "import sys;print(sys.stdin.buffer.read().decode('utf-16-le','ignore'))" \
  | grep -oE 'mservices|farsitel|\$\{[a-zA-Z]+\}' | sort -u
```

Expect only your target store's strings, and **no** `${...}`. Note that macOS's `strings`
cannot decode Android's UTF-16LE binary XML string pool and reports nothing regardless of
correctness — use the decode above instead.

---

## How market resolution works

The library reads two meta-data entries from the merged manifest at runtime:

| Meta-data key | Supplied by | Example |
|---|---|---|
| `market_id` | `${marketApplicationId}` | `ir.mservices.market` |
| `market_bind` | `${marketBindAddress}` | `ir.mservices.market.InAppBillingService.BIND` |

From `market_id` alone, the library **derives** what it trusts:

| Resolved `market_id` | Certificate pin | Broadcast connection |
|---|---|---|
| `com.farsitel.bazaar` | Bazaar's certificate | enabled above Bazaar version `801301` |
| `ir.mservices.market` | Myket's certificate | disabled |
| absent or blank | falls back to Bazaar | as Bazaar |
| anything else | **none — connection refused** | disabled |

Deriving rather than accepting these is deliberate: a host app declares only *which* store it
ships to, and cannot weaken the certificate pin or re-enable a transport. An unrecognised
`market_id` is refused outright rather than running unpinned.

### Certificate pinning

Before binding to a billing service, Poolakey verifies the installed store app is signed by
the expected certificate. Both stores' certificates are compiled into the library from
`markets.properties`. This is separate from, and additional to,
`SecurityCheck.Enable(rsaPublicKey)`, which verifies individual purchase payloads.

Myket signs with a 1024-bit RSA key issued in 2011, so the Myket pin is cryptographically
weaker than Bazaar's 2048-bit one. That is Myket's key, not something this fork can change.

---

## Behaviour differences between the two stores

Poolakey has two transports: a bound AIDL **service**, and a **broadcast receiver** fallback
used by newer Bazaar clients.

**On Myket, only the service transport is used.** The broadcast path is disabled because its
version gate compares against Bazaar's version numbering — Myket's version codes are unrelated
(Myket 9.9.7 reports `997`, Bazaar 24.3.0 reports `2400300`), so a shared numeric threshold is
meaningless. Consequences on Myket:

- a failed service bind fails immediately, with no broadcast retry;
- `checkTrialSubscription` and Bazaar's `featureConfig` are unavailable — Myket declares no
  such receivers.

Purchase, consume, query-purchases and sku-details work on both stores.

Service binding is by action + package, with no explicit component name. Upstream pinned
Bazaar's internal service class, which does not exist in Myket.

---

## Building this fork

Requires **JDK 8–14**. Gradle 6.5.1 cannot compile build scripts under JDK 17+
(`Unsupported class file major version 61`). JDK 11 is the safe choice:

```bash
export JAVA_HOME=/path/to/jdk-11
./gradlew :poolakey:assembleRelease
./gradlew :poolakey:testReleaseUnitTest
```

### Build changes vs upstream

The Bintray publishing plugin was removed. Bintray and jcenter shut down in 2021, and
`gradle-bintray-plugin:1.2` and its transitive `http-builder:0.7.2` exist in no live
repository, so upstream's build can no longer resolve its own classpath. `jcenter()` was
replaced with `mavenCentral()` + `gradlePluginPortal()`. Publishing is via JitPack. The
`bintray/` directory is left in place so the change is easy to revert.

`./gradlew detekt` fails on this repository, and did so before this fork — it is not a
regression introduced here.

---

## Verifying a store's certificate yourself

If you would rather not trust the values committed here, regenerate them. This is the same
transform the library applies at runtime (`certificate.publicKey.encoded` → SPKI DER →
uppercase colon-separated hex):

```bash
keytool -printcert -jarfile store.apk -rfc | awk '/BEGIN CERT/,/END CERT/' > cert.pem
openssl x509 -in cert.pem -pubkey -noout > pub.pem
openssl pkey -pubin -in pub.pem -outform DER \
  | xxd -p -c1 | tr 'a-f' 'A-F' | paste -sd: -
```

Compare against `bazaarReleaseHash` / `myketReleaseHash` in `markets.properties`.

---
---

# Upstream README

The original project documentation follows, with only the dead `jcenter()` line updated.

<img src="https://github.com/PHELAT/Poolakey/raw/master/asset/Poolakey.jpg"/><br/>

Android In-App Billing SDK for [Cafe Bazaar](https://cafebazaar.ir/?l=en) App Store.
## Getting Started
To start working with Poolakey, you need to add its dependency into your `build.gradle` file:
### Dependency
```groovy
dependencies {
    implementation "com.github.cafebazaar.Poolakey:poolakey:[latest_version]"
}
```

Then you need to add jitpack as your maven repository in `build.gradle`  file:

```groovy
repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
```

### How to use
For more information regarding the usage of Poolakey, please check out the [wiki](https://github.com/cafebazaar/Poolakey/wiki) page.
### Sample
There is a fully functional sample application that demonstrates the usage of Poolakey, all you have to do is cloning the project and running the [app](https://github.com/cafebazaar/Poolakey/tree/master/app) module.
### Reactive Extension Support
Yes, you've read that right! Poolakey supports Reactive Extension framework. Just add its dependency into your `build.gradle` file:
```groovy
dependencies {
    // RxJava 3
    implementation "com.github.cafebazaar.Poolakey:poolakey-rx3:[latest_version]"
    // RxJava 2
    implementation "com.github.cafebazaar.Poolakey:poolakey-rx:[latest_version]"
}
```

And instead of using Poolakey's callbacks, use the reactive fuctions:
```kotlin
payment.getPurchasedProducts()
    .subscribe({ purchasedProducts ->
        ...
    }, { throwable ->
        ...
    })
```
