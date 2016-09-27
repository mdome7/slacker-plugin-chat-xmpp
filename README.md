# Chat XMPP Plugin

Slacker components for integrating with chat services that support XMPP.

Currently supported services:
- [HipChat](http://hipchat.com)
- [Slack](http://slack.com)


# Components in this plugin:
 
## Resources
 - XMPPResource - XMPP configuration that is used by other components
 
## Collectors
 - HipChatCollector - log into HipChat as a "bot" so that other chat users can interact with it
 - SlackCollector - log into HipChat as a "bot" so that other chat users can interact with it

## Actions
(none yet)

## Endpoints
 - HipChatEndpoint - deliver response to a HipChat room


# Configuration Example

Below is a snippet of an example slacker config.yaml configuration for resources, collectors and endpoints
(no actions yet) that are in this plugin.

```
resources:
  - name: HipChatXMPP    # reference this name in the HipChatCollector, HipChatEndpoint, etc. config 
    plugin: chat-xmpp    # your JAR files are in the plugins folder e.g. plugins/chat-xmpp
    className: com.labs2160.slacker.plugin.chat.xmpp.XMPPResource
    configuration:
      user: "spiderman@chat.hipchat.com"
      password: webSlinger
      host: chat.hipchat.com
      mucNickname: spidey     # the nickname used by this bot to join rooms (must exactly match HipChat "Room nickname" configured for the user)
      mucKeyword: "@spidey"   # messages in the rooms that start with this keyword will be processed by this bot (usually uses the '@<mention>' convention in HipChat)
      mucDomain: conf.hipchat.com     # Conference (MUC) domain (e.g. conf.hipchat.com)
      msgFatalError: "My spidey senses are tingling"

  - name: SlackXMPP    # reference this name in the SlackCollector configuration
    plugin: chat-xmpp
    className: com.labs2160.slacker.plugin.chat.xmpp.XMPPResource
    configuration:
      user: "superman"
      password: justiceleague.abcdef
      host: justiceleague.xmpp.slack.com
      mucNickname: clark
      mucKeyword: "@clark"
      mucDomain: conference.justiceleague.xmpp.slack.com
      msgFatalError: "Oh noes! Kryptonite!"

collectors:
  - name:  HipChat
    plugin: chat-xmpp
    className: com.labs2160.slacker.plugin.chat.xmpp.hipchat.HipChatCollector
    enabled: true
    configuration:
      XMPPResourceRef: HipChatXMPP
      mucRooms: 1234_dailyBugle,1234_mj  # rooms to automatically join at startup

  - name:  Slack
    plugin: chat-xmpp
    className: com.labs2160.slacker.plugin.chat.xmpp.slack.SlackCollector
    enabled: false
    configuration:
      XMPPResourceRef: SlackXMPP
      mucRooms: general,dailyPlanet
      
actions:
  - name: Hello World
    alias: hello world
    action:
      className: com.labs2160.slacker.plugin.extra.StaticResponseAction
      configuration:
        response: Hello world!
    endpoints:
      - name: Echo to G Room
        plugin: chat-xmpp
        className: com.labs2160.slacker.plugin.chat.xmpp.hipchat.HipChatEndpoint
        configuration:
          XMPPResourceRef: HipChatXMPP  # references HipChat XMPPResource name
          mucRooms: 1234_my_room
```
