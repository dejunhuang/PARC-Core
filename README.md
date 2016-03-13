## PARC-Core

This is a project specialized for [PARC-WebApp](https://github.com/dejunhuang/PARC-WebApp) application. It includes the core utility for a [Privacy-preserving Data Cleaning framework](http://macsphere.mcmaster.ca/bitstream/11375/18075/2/gairola_dhruv_201507_msc_computer_science.pdf). It provides the wrapper for main features in the paper.

### Working Environment

Java 7 64-bit, Maven 3.0.3

### Installation 

This is a maven project, please download and install maven first. Please check the related tutorial [here](https://maven.apache.org/install.html).

To check if you install maven correctly, use the following command in your terminal:

```
mvn -version
```

Install the project into the local maven repository:

```
git clone https://github.com/dejunhuang/PARC-Core.git
cd PARC-Core
mvn clean install
```

You can use the project as the wrapper Java Library in your project.