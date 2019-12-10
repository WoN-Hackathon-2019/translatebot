# Setup Project on macOS

As I had some problems setting up the project on mac, I decided to write a how to for this.

1. Install IntelliJ IDEA: https://www.jetbrains.com/de-de/idea/download/#section=mac (As a TU student you get a free license for the professional Edition, therefore I would recommend that)

2. Download maven (https://maven.apache.org/download.cgi) (I used the binary zip), unzip it and place it where you like, then set the steps as stated in the README.

3. Now comes the tricky part as it was pretty difficult for me to find an openJDK version 8 which actually works on macOS. The following worked for me:
- install brew.sh: https://brew.sh/index_de
- brew tap AdoptOpenJDK/openjdk
- brew cask install adoptopenjdk8

4. Now open the pom.xml with IntelliJ

Congratulations, you're all set.
