DataAnalysis
============

#### Gzip File Parsing Demo

This demo parses data from a gzipped csv file and calculates some summary statistics.
Only the statistical information is stored in memory, so the demo should able to parse fairly
large data sets.
   
How to run:

Install JDK SE 8 or later http://www.oracle.com/technetwork/java/javase/downloads/index.html

Run from a command prompt to build:

```
$ javac DataAnalysis.java
```
	
Then run:

```
$ java DataAnalysis smallSampleInput.csv.gz
```

Or run to see any parsing errors:

```
$ java DataAnalysis -p smallSampleInput.csv.gz
```
