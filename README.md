# Touchless Kiosk

## Overview
Customer scans the QR code on a public kiosk to mirror the kiosk's screen to his phone to use it, thereby avoiding touching the Kiosk screen.

## Demo
https://github.com/SavanK/touchless-kiosk/blob/main/demo/touchless-kiosk-demo.mp4


https://user-images.githubusercontent.com/26939211/136629569-5df77904-dd40-4539-bcd2-a480528a37e1.mp4

## High level design
* Uses webRTC to share kiosk screen to customer's phone
* On the customer's phone, 
  * webRTC client is written in javascript and hence can be rendered as a webpage in any browser (quick and convenient as no app installation needed)
  * captures user's touch events on the streamed screen and transmits to kiosk
* On the Android kiosk's side,
  * a foreground service streams the kiosk screen through webRTC
  * an accessibility service injects touch events received from customer as gestures
* A Ktor application hosted on heroku acts as both,
  * a signalling server for webRTC session (after initial handshake between customer and kiosk done through Ktor app, the video is streamed through peer-to-peer communication)
  * maintains active list of registered kiosks to which customers can connect to
  * handles session initiation and teardown between a customer and a kiosk
