'use strict';

const SUCCESS = "success";
const FAILURE = "failure";

const CUSTOMER_ID = "customerId";
const KIOSK_ID = "kioskId";
const CUSTOMER = "customer";
const KIOSK = "kiosk";
const REQUEST_ID = "requestId";
const PAYLOAD = "payload";
const WEB_RTC_PAYLOAD = "webRtcPayload";
const MESSAGE = "message";
const RESULT = "result";
const MOUSE_EVENT_PAYLOAD = "mouseEventPayload";
const MOUSE_EVENT_TYPE = "mouseEventType";
const TIMESTAMP = "timeStamp";
const X_POS = "x";
const Y_POS = "y";
const MOUSE_DOWN = "mouseDown";
const MOUSE_MOVE = "mouseMove";
const MOUSE_UP = "mouseUp";

const REQUEST_CONNECT_KIOSK = "connect_kiosk";
const REQUEST_WEB_RTC_TRANSPORT = "web_rtc_transport";
const REQUEST_DISCONNECT_KIOSK = "disconnect_kiosk";
const REQUEST_MOUSE_EVENT = "mouse_event";

const MESSAGE_TYPE_REQUEST = "request";
const MESSAGE_TYPE_RESPONSE = "response";

const connectButton = document.getElementById('connect');
connectButton.addEventListener('click', connect);

const disconnectButton = document.getElementById('disconnect');
disconnectButton.addEventListener('click', disconnect);

const wsUri = "wss://touchless-kiosk.herokuapp.com/customer";
let websocket;
let activeConnection;
const customerId = uuidv4();
let peerConnection;
const configuration = { iceServers: [{ url: 'stun:stun.l.google.com:19302' }] };
const offerOptions = {
  offerToReceiveAudio: 1,
  offerToReceiveVideo: 1
};
let startTime;
const remoteVideo = document.getElementById('remoteVideo');
var lastMoveEventTime = 0;

function uuidv4() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
    var r = Math.random() * 16 | 0, v = c == 'x' ? r : (r & 0x3 | 0x8);
    return v.toString(16);
  });
}

function getQueryParameter(param){
   if(param=(new RegExp('[?&]'+encodeURIComponent(param)+'=([^&]*)')).exec(location.search))
      return decodeURIComponent(param[1]);
}

function getMessageType(message) {
  if(message.hasOwnProperty(REQUEST_ID) && message.hasOwnProperty(PAYLOAD) && message.hasOwnProperty(WEB_RTC_PAYLOAD)) {
    if(message.hasOwnProperty(RESULT) && message.hasOwnProperty(MESSAGE)) {
      return MESSAGE_TYPE_RESPONSE;
    } else {
      return MESSAGE_TYPE_REQUEST;
    }
  }
  return '';
}

function constructRequest(requestId, customerId, kioskId, webRtcPayload, mouseEventPayload) {
  var customer = {};
  customer[CUSTOMER_ID] = customerId;
  var kiosk = {};
  kiosk[KIOSK_ID] = kioskId;
  var connection = {};
  connection[CUSTOMER] = customer;
  connection[KIOSK] = kiosk;
  var request = {};
  request[REQUEST_ID] = requestId;
  request[PAYLOAD] = JSON.stringify(connection);
  request[WEB_RTC_PAYLOAD] = webRtcPayload;
  request[MOUSE_EVENT_PAYLOAD] = mouseEventPayload;
  return request;
}

function consrtuctResponse(requestId, customerId, kioskId, webRtcPayload, result, message) {
  var customer = {};
  customer[CUSTOMER_ID] = customerId;
  var kiosk = {};
  kiosk[KIOSK_ID] = kioskId;
  var connection = {};
  connection[CUSTOMER] = customer;
  connection[KIOSK] = kiosk;
  var response = {};
  response[REQUEST_ID] = requestId;
  response[PAYLOAD] = JSON.stringify(connection);
  response[WEB_RTC_PAYLOAD] = webRtcPayload;
  response[MESSAGE] = message;
  response[RESULT] = result;
  return response;
}

function constructMouseEvent(mouseEventType, timeStamp, x, y) {
  var mouseEvent = {};
  mouseEvent[MOUSE_EVENT_TYPE] = mouseEventType;
  mouseEvent[TIMESTAMP] = timeStamp;
  mouseEvent[X_POS] = x;
  mouseEvent[Y_POS] = y;
  return mouseEvent;
}

async function connect() {
  console.log('CONNECTING ...');

  if(websocket != null) {
    console.log('tearing down previous WebSocket connection');
    await disconnect();
  }

  websocket = new WebSocket(wsUri);
  websocket.onopen = function(evt) { onOpen(evt) };
  websocket.onclose = function(evt) { onClose(evt) };
  websocket.onmessage = function(evt) { onMessage(evt) };
  websocket.onerror = function(evt) { onError(evt) };
}

function onOpen(evt) {
  console.log('CONNECTED');
  doSend(JSON.stringify(constructRequest(REQUEST_CONNECT_KIOSK, customerId, getQueryParameter('kiosk'), "", "")));
}

function onClose(evt) {
  console.log('DISCONNECTED');
  cleanup();
}
    
function onMessage(evt) {
  const message = JSON.parse(String.raw`${evt.data}`);
  if(message.hasOwnProperty(REQUEST_ID)) {
    switch(getMessageType(message)) {
      case MESSAGE_TYPE_REQUEST:
        switch(message.requestId) {
          case REQUEST_WEB_RTC_TRANSPORT:
            console.log(`REQUEST: web_rtc_transport`);
            const webRtcPayload = JSON.parse(message.webRtcPayload);
            if(webRtcPayload.hasOwnProperty('type')) {
              switch(webRtcPayload.type) {
                case 'candidate':
                  iceReceived(new RTCIceCandidate(webRtcPayload));
                  break;
                case 'offer':
                  offerReceived(new RTCSessionDescription(webRtcPayload));
                  break;
              }
            }
            break;
        }
        break;
      case MESSAGE_TYPE_RESPONSE:
        switch(message.requestId) {
          case REQUEST_CONNECT_KIOSK:
            console.log(`RESPONSE: connect_kiosk ${message.result}`);
            if(message.result == SUCCESS) {
              activeConnection = JSON.parse(message.payload);
              console.log(`New active connection: ${activeConnection}`);
            } else {
              activeConnection = null;
            }
            break;
          case REQUEST_DISCONNECT_KIOSK:
            console.log(`RESPONSE: disconnect_kiosk ${message.result}`);
            cleanup();
            break;
          case REQUEST_WEB_RTC_TRANSPORT:
            console.log(`RESPONSE: web_rtc_transport ${message.result}`);
            break;
        }
        break;
    }
  }
}

async function offerReceived(offer) {
  console.log(`offerReceived...offer: ${JSON.stringify(offer.toJSON())}`);
  startTime = window.performance.now();

  peerConnection = new RTCPeerConnection(configuration);
  console.log('Created remote peer connection object peerConnection');
  peerConnection.onicecandidate = onIceCandidate;
  peerConnection.oniceconnectionstatechange = onIceStateChange;
  peerConnection.ontrack = gotRemoteStream;

  console.log('peerConnection setRemoteDescription start');
  try {
    await peerConnection.setRemoteDescription(offer);
    console.log('peerConnection setRemoteDescription complete');
  } catch (e) {
    console.log(`Failed to set session description: ${e.toString()} \n stack trace: ${e.stack}`);
  }

  console.log('peerConnection createAnswer start');
  // Since the 'remote' side has no media stream we need
  // to pass in the right constraints in order for it to
  // accept the incoming offer of audio and video.
  try {
    const answer = await peerConnection.createAnswer();
    peerConnection.setLocalDescription(answer);
    console.log(`Sending answer: ${JSON.stringify(answer)} to kiosk...`);
    // Send answer success back to Kiosk
    doSend(JSON.stringify(consrtuctResponse(REQUEST_WEB_RTC_TRANSPORT, 
      activeConnection.customer.customerId, 
      activeConnection.kiosk.kioskId, 
      JSON.stringify(answer), 
      SUCCESS,
      "")));
    console.log('successfully sent answer back');
  } catch (e) {
    console.log(`Failed to create answer and set answer: ${e.toString()} \n stack trace: ${e.stack}`);
  }
}

async function iceReceived(ice) {
  try {
    console.log(`iceReceived ${JSON.stringify(ice.toJSON())}`);
    await peerConnection.addIceCandidate(ice);
    console.log(`peerConnection addIceCandidate success`);
  } catch (e) {
    console.log(`peerConnection failed to add ICE Candidate: ${e.toString()}`);
  }
}

function gotRemoteStream(evt) {
  if (remoteVideo.srcObject !== evt.streams[0]) {
    remoteVideo.srcObject = evt.streams[0];
    console.log('peerConnection received remote stream');
  }
}

function onError(evt) {
  console.log('ERROR: ' + evt.data);
  cleanup();
}

async function doSend(message) {
  console.log("SENT: " + message);
  websocket.send(message);
}

function cleanup() {
  console.log('cleanup');
  hangup();
  activeConnection = null;
  if(websocket != null) {
    websocket.close();
  }
  websocket = null;  
}

async function disconnect() {
  console.log("DISCONNECTING ...");
  doSend(JSON.stringify(constructRequest(REQUEST_DISCONNECT_KIOSK, customerId, getQueryParameter('kiosk'), "", "")));
  // do cleanup after getting response back from server for disconnection request
}

remoteVideo.addEventListener('mousedown', function(e) {
  sendMouseEvent(MOUSE_DOWN, e.timeStamp, e.x, e.y);
})

remoteVideo.addEventListener('mousemove', function(e) {
  sendMouseEvent(MOUSE_MOVE, e.timeStamp, e.x, e.y);
})

remoteVideo.addEventListener('mouseup', function(e) {
  sendMouseEvent(MOUSE_UP, e.timeStamp, e.x, e.y);
})

var touchId = null;

remoteVideo.addEventListener('touchstart', function(e) {
  e.preventDefault();
  if(touchId == null) {
    var touch = e.targetTouches.item(0);
    touchId = touch.identifier;
    sendMouseEvent(MOUSE_DOWN, e.timeStamp, touch.clientX, touch.clientY);
  }
})

remoteVideo.addEventListener('touchmove', function(e) {
  e.preventDefault();
  if(touchId != null) {
    for(var i=0;i<e.targetTouches.length;i++) {
      var touch = e.targetTouches.item(i);
      if(touch.identifier==touchId) {
        sendMouseEvent(MOUSE_MOVE, e.timeStamp, touch.clientX, touch.clientY);
        break;
      }
    }
  }
})

remoteVideo.addEventListener('touchend', function(e) {
  e.preventDefault();
  if(touchId != null) {
    sendMouseEvent(MOUSE_UP, e.timeStamp, 0, 0);
    touchId = null;
  }
})

var isDown = false;

function sendMouseEvent(mouseEventType, timeStamp, x, y) {
  if(activeConnection != null) {
    isDown = isDown || mouseEventType==MOUSE_DOWN;

    if(isDown) {
      if(mouseEventType==MOUSE_MOVE && timeStamp-lastMoveEventTime<=50) {
        // skip
        return;
      }

      if(mouseEventType==MOUSE_MOVE) {
        lastMoveEventTime = timeStamp;
      }
      var rect = remoteVideo.getBoundingClientRect();
      var xPos = Math.floor(x - rect.left)/remoteVideo.videoWidth;
      var yPos = Math.floor(y - rect.top)/remoteVideo.videoHeight;
      console.log(`${mouseEventType}, t=${timeStamp}, x=${xPos}, y=${yPos}`);
      doSend(JSON.stringify(constructRequest(REQUEST_MOUSE_EVENT,
        activeConnection.customer.customerId,
        activeConnection.kiosk.kioskId,
        "",
        JSON.stringify(constructMouseEvent(mouseEventType, timeStamp, xPos, yPos)))));

      if(mouseEventType==MOUSE_UP) {
        isDown = false;
      }
    }
  }
}

remoteVideo.addEventListener('loadedmetadata', function() {
  console.log(`Remote video videoWidth: ${this.videoWidth}px,  videoHeight: ${this.videoHeight}px`);
});

remoteVideo.addEventListener('resize', () => {
  console.log(`Remote video size changed to ${remoteVideo.videoWidth}x${remoteVideo.videoHeight}`);
  // We'll use the first onsize callback as an indication that video has started
  // playing out.
  if (startTime) {
    const elapsedTime = window.performance.now() - startTime;
    console.log('Setup time: ' + elapsedTime.toFixed(3) + 'ms');
    startTime = null;
  }
  /*var videoAR = remoteVideo.videoHeight/remoteVideo.videoWidth;
  var newHeight = remoteVideo.videoHeight;
  var newWidth = remoteVideo.videoWidth;
  console.log(`screen.availHeight=${window.screen.availHeight}`);
  if(remoteVideo.videoHeight > window.screen.availHeight) {
    console.log("resizing video");
    var newHeight = window.screen.availHeight * 0.6;
    var newWidth = newHeight/videoAR;
    console.log(`newHeight: ${newHeight} newWidth: ${newWidth}`);
  }
  remoteVideo.height = newHeight;
  remoteVideo.width = newWidth;*/
});

async function onIceCandidate(evt) {
  try {
    const ice = evt.candidate;
    console.log(`Sending ice: ${JSON.stringify(ice)} to kiosk...`);
    // Send ice success back to Kiosk
    doSend(JSON.stringify(consrtuctResponse(REQUEST_WEB_RTC_TRANSPORT, 
      activeConnection.customer.customerId, 
      activeConnection.kiosk.kioskId, 
      JSON.stringify(ice), 
      SUCCESS,
      "")));
    console.log('successfully sent ice back');
  } catch (e) {
    console.log(`Failed to create send ice: ${e.toString()} \n stack trace: ${e.stack}`);
  }
}

function onIceStateChange(evt) {
  console.log(`peerConnection ICE state: ${peerConnection.iceConnectionState}`);
  console.log('ICE state change event: ', evt);
}

function hangup() {
  console.log('Ending call');
  if(peerConnection != null) {
    peerConnection.close();
  }
  peerConnection = null;
}