
# Install Java
sudo apt update
sudo apt install -y openjdk-8-jdk

# Install sbt
echo "deb https://dl.bintray.com/sbt/debian /" | sudo tee -a /etc/apt/sources.list.d/sbt.list
curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | sudo apt-key add
sudo apt-get update
sudo apt-get install -y sbt

# Install selenium
sudo apt-get update
sudo apt-get install -y unzip xvfb libxi6 libgconf-2-4
sudo apt-get -y install firefox

wget https://github.com/mozilla/geckodriver/releases/download/v0.26.0/geckodriver-v0.26.0-linux64.tar.gz
tar xzf geckodriver-v0.26.0-linux64.tar.gz
sudo mv geckodriver /usr/bin/geckodriver

mkdir ~/selenium && cd ~/selenium
wget https://selenium-release.storage.googleapis.com/3.141/selenium-server-standalone-3.141.59.jar

wget http://www.java2s.com/Code/JarDownload/testng/testng-6.5.1.jar.zip
unzip testng-6.5.1.jar.zip

screen -d -m -S selenium bash -c 'DISPLAY=:1 xvfb-run java -jar ~/selenium/selenium-server-standalone-3.141.59.jar'

#git
git clone https://github.com/chrischivers/multi-city-flight-scraper.git

mkdir multi-city-flight-scraper/src/main/resources
#vim multi-city-flight-scraper/src/main/resources/application.conf
#vim multi-city-flight-scraper/src/main/resources/search-config.json

#cd multi-city-flight-scraper
#sbt run



# user data as root

#!/bin/bash
sudo -u ubuntu screen -d -m -S selenium bash -c 'DISPLAY=:1 xvfb-run java -jar /home/ubuntu/selenium/selenium-server-standalone-3.141.59.jar'
cd /home/ubuntu/multi-city-flight-scraper
git pull
sudo -u ubuntu screen -d -m -S sbt bash -c 'sbt run'