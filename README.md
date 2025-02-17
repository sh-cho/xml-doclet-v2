# xml-doclet-v2

[![JitPack version badge](https://jitpack.io/v/sh-cho/xml-doclet-v2.svg)](https://jitpack.io/#sh-cho/xml-doclet-v2)

Doclet generating Javadoc as XML with [the new Doclet API](https://openjdk.org/groups/compiler/using-new-doclet.html).

> [!NOTE]
>
> Currently only generates limited information, only for [`typescript-generator`](https://github.com/vojtechhabarta/typescript-generator) to [include Javadoc comments](https://github.com/vojtechhabarta/typescript-generator/wiki/Javadoc) in generated TypeScript definitions.


This generates Javadoc XML format originally defined by [MarkusBernhardt/xml-doclet](https://github.com/MarkusBernhardt/xml-doclet) - See [javadoc.xsd](https://github.com/MarkusBernhardt/xml-doclet/blob/6bb0cc1ff82b2e20787b93252c6b294d0eb31622/src/main/xjc/javadoc.xsd).

## Supported Java versions
- 17 and later

## Usage

### Manual
```sh
javadoc \
  -classpath '...' \
  -d '/.../output/directory' \
  -doclet 'io.github.shcho.xmldoclet.XmlDoclet' \
  -docletpath '/.../xml-doclet-v2-{version}.jar' \
  -filename 'javadoc.xml' \
  '...java files...'
```

### Gradle (Groovy DSL)
ex) when using [Lombok](https://projectlombok.org/)
```groovy
plugins {
    id('io.freefair.lombok') version '8.11'
}

configurations {
    xmlDoclet
}

repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

configurations {
    xmlDoclet
}

dependencies {
    annotationProcessor 'org.projectlombok:lombok'
    compileOnly 'org.projectlombok:lombok'
    testCompileOnly 'org.projectlombok:lombok'
    testAnnotationProcessor 'org.projectlombok:lombok'

    // from JitPack
    xmlDoclet "com.github.sh-cho:xml-doclet-v2:${xmlDocletVersion}"

    // from Maven Central (WIP, Not yet published, group name can be changed later)
    // xmlDoclet "io.github.sh-cho:xml-doclet-v2:${xmlDocletVersion}"
}

tasks.register('xmldoc', Javadoc) {
    dependsOn('classes')
    dependsOn('delombok')

    source = delombok
    classpath = files(
            configurations.compileClasspath,
            sourceSets.main.runtimeClasspath,
            sourceSets.main.output.classesDirs,
            sourceSets.main.output.resourcesDir,
    )
    group = 'documentation'

    destinationDir = reporting.file("xmlDoclet")  // ex. "build/reports/xmlDoclet/javadoc.xml"
    include('**/dto/*.java')
    exclude('com.acme.*.dto.**$*Builder',
            'com.acme.*.dto.**$*BuilderImpl')

    options {
        docletpath = configurations.xmlDoclet.files as List
        doclet = "io.github.shcho.xmldoclet.XmlDoclet"

        //// Additional options
        // addStringOption("-Xfilename", "custom-output-name.xml")
        // addStringOption("-Xescape", "false")  // If you want to unescape strings in Javadoc comments
    }
}

```

## Options
```
# Standard doclet options
-d [<directory>]
              Destination directory for output file. (Default: .)

# Custom options (prefix with -X)
-Xfilename [<filename>]
              Output filename. (Default: javadoc.xml)
-Xescape [true|false]
              Escape characters in javadoc comments. (Default: true)
```

## References
- [MarkusBernhardt/xml-doclet](https://github.com/MarkusBernhardt/xml-doclet) - Supports JDK 8
- [manticore-projects/xml-doclet](https://github.com/manticore-projects/xml-doclet) (fork of above) - Supports JDK 11 and later, via enforcing Java toolchain version of 11
- https://github.com/vojtechhabarta/typescript-generator

## TODO
- [ ] Publish to Maven Central
  - Package name might be changed

## License
[Apache License 2.0](LICENSE)
