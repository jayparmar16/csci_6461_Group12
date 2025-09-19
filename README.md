## How to Compile and Package the Assembler into a JAR File

Follow these steps to compile the Java source code and create a runnable `Project0.jar` file.

### 1. Compile the Java Source Files

Open your terminal and run:

```sh
javac -d out src/*.java
```
- This compiles all `.java` files in the `src` directory and places the `.class` files in the `out` directory.

---

### 2. Create the Manifest File

Create a file named `manifest.txt` in the project root with the following content (ensure there is a blank line at the end):

```
Main-Class: Main
```

---

### 3. Create the JAR File

Run this command from your project root:

```sh
jar cfm Project0.jar manifest.txt -C out .
```
- This packages the compiled classes into `Project0.jar` with the correct entry point.

---

### 4. Run the JAR File

Test your JAR file to ensure it works:

```sh
java -jar Project0.jar
```

---

### Folder Structure Example

```
  /src
    Main.java
    ...
  /out
    (compiled .class files)
  manifest.txt
  Project0.jar
```

---
