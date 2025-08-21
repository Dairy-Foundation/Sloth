# Sloth

<a href="https://repo.dairy.foundation/#/releases/dev/frozenmilk/sinister/Sloth" target="_blank">
<img src="https://repo.dairy.foundation/api/badge/latest/releases/dev/frozenmilk/sinister/Sloth?color=40c14a&name=Sloth" />
</a>

Sloth is the fastest hot code reload library for FTC.

Hot reloading involves sending only your teamcode to the robot when you make a
change, to get *very* fast Write -> Run -> Test development cycles. Say goodbye to
waiting 40+ seconds for your teamcode to be uploaded, Sloth gets code on your
robot in ***under a second***.

Sloth is also a [Sinister](https://docs.dairy.foundation/Sinister/overview)
runtime that enables the use of Sinister's classpath scanning and dynamic
loading capabilities on the android FTC platform. This allows Sloth to
support a wide range of libraries that need to know about and react to hot
reloading your code.

Sloth has some major improvements over its predecessor, [fastload](https://github.com/MatthewOates36/fast-load):
1. Sloth is **much faster** than fastload. fastload advertises ~7 seconds
   upload time; Sloth has a upper ceiling of 2 seconds, but often is less than 1.
3. Sloth will **keep changes across restarts and power cycles of the robot**.
4. Sloth only processes the change in code when your OpMode ends, which means
   **it is safe to deploy with Sloth while running other code**.
6. `@Pinned` can be put on classes to prevent dynamically changing it, or any subclasses of it.
7. Sloth is a more capable runtime, that does more than just swap over your code:
    - Sloth sets up [Dairy](https://docs.dairy.foundation/introduction).
    - Sloth updates parts of the SDK properly when you upload code changes, including
      your hardwaremap if you're uploading drivers with your teamcode (e.g. the
      goBILDA Pinpoint driver before it was part of the SDK).
    - Libraries that rely on looking at your code, like
      [FTC Dashboard](https://github.com/acmerobotics/ftc-dashboard), can be
      easily tweaked to be compatible with Sloth, and thus load faster, and
      support hotreloading. (Please open an issue if you have a favourite library
      that is currently incompatible with Sloth; I don't mind maintaining a fork,
      or doing the setup work to make it easy for others to maintain.)
    - Sloth supports custom user classpath scanning, so you can write your own
      systems that listen and react to hot reloads.
8. Sloth includes a drop-in replacement of FTC Dashboard that replaces some internal
   mechanisms of FTC Dashboard to use the Sloth and Sinister equivalents.
   This fork fully supports hot reloading for Configuration (`@Config`) and OpModes.

There are some precautions to take when using Sloth:
1. Sloth will only dynamically hot reload classes in the `org.firsinspires.ftc.teamcode` package (and subpackages).
2. It is possible to upload code that compiles, but does not work when hot reloaded due to:
   - Installing or changing libraries.
   - Changing files that are not hot reloaded.
   - Changing `@Pinned` on files.
   Be careful to ensure that you make changes that will be changed, and if make changes that will not,
   that you perform a full install in order to propagate them.

> [!WARNING]
> If you used an older version of the Pedro Pathing quickstart, you will need
> to move your files to the `org.firstinspires.ftc.teamcode` package.

# Installation

> [!IMPORTANT]
> If you are already using Dairy, skip straight to the [Dairy Core](#dairy-core) section.

> [!NOTE]
> If you are using FTC Dashboard, check out the [FTC Dashboard](#ftc-dashboard) section.

## Sloth Library
Add the dairy releases repository to your `TeamCode` `build.gradle`, above the `dependencies` block:
```groovy
repositories {
    maven {
        url = "https://repo.dairy.foundation/releases"
    }
}
```

Then add sloth to the `dependencies` block:
```groovy
dependencies {
    implementation("dev.frozenmilk.sinister:Sloth:0.2.4")
}
```

Now [install the Load plugin](#load-plugin).

## Dairy Core
To use this release of Sloth with Dairy you need to install a snapshot version of Dairy's Core.

Add the Dairy releases and snapshots repository to your `TeamCode` `build.gradle`, above the `dependencies` block:
```groovy
repositories {
    maven {
        url = "https://repo.dairy.foundation/releases"
    }
    maven {
        url = "https://repo.dairy.foundation/snapshots"
    }
}
```

Then add core to the `dependencies` block:
```groovy
dependencies {
    implementation("dev.frozenmilk.dairy:Core:2.2.4")
}
```

> [!WARNING]
> You do not need to install Sloth as well, and if you currently have any installations of either
> `"dev.frozenmilk.dairy:Util"` or `"dev.frozenmilk:Sinister"` then you need to remove those, as this Core
> version will provide the correct versions of these libraries.

Now [install the Load plugin](#load-plugin).

## Load Plugin
Add this to the top of your `TeamCode` `build.gradle`:
```groovy
buildscript {
    repositories {
        mavenCentral()
        maven {
            url "https://repo.dairy.foundation/releases"
        }
    }
    dependencies {
        classpath "dev.frozenmilk:Load:0.2.4"
    }
}
```

Add this after the `apply plugin:` lines in the same file:
```groovy
// there should be 2 or 3 more lines that start with 'apply plugin:' here
apply plugin: 'dev.frozenmilk.sinister.sloth.load'
```

Sync and download the code onto your robot via standard install.

Now [add the Gradle tasks](#gradle-tasks).

NOTE: If you use FTC Dashboard, install that now, then setup the gradle tasks:

## FTC Dashboard
Add the dairy releases repository to your `TeamCode` `build.gradle`, above the `dependencies` block (if you already have it, no need to do so again)
```groovy
repositories {
    maven {
        url = "https://repo.dairy.foundation/releases"
    }
}
```

Then add dashboard to the `dependencies` block:
```groovy
dependencies {
    implementation("com.acmerobotics.slothboard:dashboard:0.2.4+0.4.17")
}
```

> [!NOTE]
> If you use a library that imports dashboard via a `implementation` or `api` dependency,
> ask the library maintainers to consider changing it to `compileOnly`.  This will allow
> it to work with the modified version of FTC Dashboard that Sloth uses.

Change the `implementation` like so:
```groovy
implementation("com.pedropathing:pedro:1.0.8") {
   exclude group: "com.acmerobotics.dashboard"
}
```
```groovy
implementation("com.acmerobotics.roadrunner:ftc:0.1.21") {
   exclude group: "com.acmerobotics.dashboard"
}
implementation ("com.acmerobotics.roadrunner:actions:1.0.1"){
   exclude group: "com.acmerobotics.dashboard"
}
```
Note that both Pedro Pathing and Road Runner require this.

_Pedro Pathing and Road Runner version numbers may not be up to date; they are provided only as an example._

## Gradle Tasks

Edit configurations:

![](image/edit_configurations.png)

Add new configuration:

![](image/add_new_configuration.png)

Select gradle:

![](image/add_new_gradle_configuration.png)

Add `deploySloth` and save it:

![](image/add_deploySloth_task.png)
NOTE: android studio will not auto complete the names of these tasks, just write it and it will work.

Edit TeamCode configuration:

![](image/edit_TeamCode_configuration.png)

Add new gradle task:

![](image/run_gradle_task.png)

Add `removeSlothRemote`:

![](image/add_removeSlothRemote_task.png)

Note: Type `:TeamCode` into the `Gradle Project` box to get the right contents,
**do not copy mine**.

Put `removeSlothRemote` first and save:

![](image/ensure_order.png)

Run the deploySloth task you just added to deploy the code.

Congratulations!  You are now set up with lightning-fast software deployment using Sloth.
