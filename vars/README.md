# Shared Libraries for Repairnator in Jenkins PipeLine

## Directory Structure

The directory structure of a Shared Library repository is as follows:

````bash
(root)
+- src                     # Groovy source files
|   +- org
|       +- foo
|           +- Bar.groovy  # for org.foo.Bar class
+- vars
|   +- foo.groovy          # for global 'foo' variable
|   +- foo.txt             # help for 'foo' variable
+- resources               # resource files (external libraries only)
|   +- org
|       +- foo
|           +- bar.json    # static helper data for org.foo.Bar
````

Refer to Jenkins Book: https://www.jenkins.io/doc/book/pipeline/shared-libraries/ 