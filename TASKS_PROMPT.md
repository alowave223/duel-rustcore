[22:14:58 INFO]: [/100.119.181.62:52064|alowave322|1.21.11] <-> ServerConnector [lobby1] has connected
[22:14:58 INFO] [TAB]: [DEBUG] [Proxy Support] Encoding message PlayerJoin(uniqueId=bd17e774-ae01-3b24-9d4f-0ca6cf0e82fc, tablistId=bd17e774-ae01-3b24-9d4f-0ca6cf0e82fc, name=alowave322, server=me.neznamy.tab.shared.data.Server@11c6e6da, vanished=false, staff=false, skin=null)
[22:14:59 INFO] [TAB]: [DEBUG] Player join of alowave322 processed in 8ms
[22:14:59 INFO] [TAB]: [DEBUG] Loaded MySQL data of alowave322
[22:14:59 INFO] [TAB]: [DEBUG] Player alowave322 received scoreboard called sb-1, disabling TAB's scoreboard slotting.
[22:15:01 INFO] [TAB]: [DEBUG] [Proxy Support] Decoded message PlayerJoin(uniqueId=c88efb3b-af69-3051-b340-c555c9ce7e4e, tablistId=c88efb3b-af69-3051-b340-c555c9ce7e4e, name=alowave2, server=me.neznamy.tab.shared.data.Server@11c6e6da, vanished=false, staff=false, skin=null)
[22:15:01 INFO] [TAB]: [DEBUG] [PlayerList] onJoin(ProxyPlayer): player=alowave2 vanished=false tabFormat=null featureDisabled=false connectionState=CONNECTED
[22:15:01 INFO] [TAB]: [DEBUG] [PlayerList] formatPlayerForEveryone(ProxyPlayer): player=alowave2 vanished=false tabFormat=null featureDisabled=false tablistId=c88efb3b-af69-3051-b340-c555c9ce7e4e connectionState=CONNECTED
[22:15:01 INFO] [TAB]: [DEBUG] [PlayerList] Skipping: player=alowave2 tabFormat is null (not loaded yet)
[22:15:05 INFO]: [/100.119.181.62:52064|alowave322|1.21.11|47ms] <-> ServerConnector [lobby2] has connected
[22:15:05 INFO]: [/100.119.181.62:52064|alowave322|1.21.11|47ms] <-> DownstreamBridge <-> [lobby1] has disconnected
[22:15:05 INFO] [TAB]: [DEBUG] [Proxy Support] Encoding message ServerSwitch(playerId=bd17e774-ae01-3b24-9d4f-0ca6cf0e82fc, newServer=me.neznamy.tab.shared.data.Server@66843de7)
[22:15:05 INFO] [TAB]: [DEBUG] [PlayerList] onDisableConditionChange(TabPlayer): player=alowave322 disabledNow=false proxy=present currentDisabled=false
[22:15:05 INFO] [TAB]: [DEBUG] [PlayerList] Sending UpdateDisableCondition proxy message: player=alowave322 featureName=Tablist name formatting disabled=false
[22:15:05 INFO] [TAB]: [DEBUG] [Proxy Support] Encoding message UpdateDisableCondition(playerId=bd17e774-ae01-3b24-9d4f-0ca6cf0e82fc, featureName=Tablist name formatting, disabled=false)
[22:15:05 INFO] [TAB]: [DEBUG] [PlayerList] Feature re-enabled for local player=alowave322, calling formatPlayerForEveryone
[22:15:05 INFO] [TAB]: [DEBUG] [Proxy Support] Encoding message PlayerListProxyPlayerData(id=1, playerId=bd17e774-ae01-3b24-9d4f-0ca6cf0e82fc, player=alowave322, prefix=%luckperms_prefix%, name=alowave322, suffix=%luckperms_suffix%)
[22:15:05 INFO] [TAB]: [DEBUG] [NameTag] onDisableConditionChange(TabPlayer): player=alowave322 disabledNow=false proxy=present teamName=Calowave322A teamHandlingPaused=false currentDisabled=false
[22:15:05 INFO] [TAB]: [DEBUG] [NameTag] Sending UpdateDisableCondition proxy message: player=alowave322 featureName=NameTags disabled=false
[22:15:05 INFO] [TAB]: [DEBUG] [Proxy Support] Encoding message UpdateDisableCondition(playerId=bd17e774-ae01-3b24-9d4f-0ca6cf0e82fc, featureName=NameTags, disabled=false)
[22:15:05 INFO] [TAB]: [DEBUG] [NameTag] Feature re-enabled for local player=alowave322, registering team=Calowave322A
[22:15:05 INFO] [TAB]: [DEBUG] Player alowave322 received scoreboard called sb-1, disabling TAB's scoreboard slotting.
[22:15:09 INFO] [TAB]: [DEBUG] [Proxy Support] Decoded message ServerSwitch(playerId=c88efb3b-af69-3051-b340-c555c9ce7e4e, newServer=me.neznamy.tab.shared.data.Server@66843de7)
[22:15:09 INFO] [TAB]: [DEBUG] [Proxy Support] Decoded message UpdateDisableCondition(playerId=c88efb3b-af69-3051-b340-c555c9ce7e4e, featureName=NameTags, disabled=false)
[22:15:09 INFO] [TAB]: [DEBUG] [UpdateDisableCondition] Processing: playerId=c88efb3b-af69-3051-b340-c555c9ce7e4e featureName=NameTags disabled=false knownProxyPlayers=1
[22:15:09 INFO] [TAB]: [DEBUG] [UpdateDisableCondition] Found target: name=alowave2 currentDisabled=false newDisabled=false connectionState=CONNECTED
[22:15:09 INFO] [TAB]: [DEBUG] [UpdateDisableCondition] State unchanged for player=alowave2 featureName=NameTags disabled=false, skipping dispatch
[22:15:09 INFO] [TAB]: [DEBUG] [Proxy Support] Decoded message PlayerListProxyPlayerData(id=1, playerId=c88efb3b-af69-3051-b340-c555c9ce7e4e, player=alowave2, prefix=%luckperms_prefix%, name=alowave2, suffix=%luckperms_suffix%)
[22:15:09 INFO] [TAB]: [DEBUG] [Proxy Support] Decoded message UpdateDisableCondition(playerId=c88efb3b-af69-3051-b340-c555c9ce7e4e, featureName=Tablist name formatting, disabled=false)
[22:15:09 INFO] [TAB]: [DEBUG] [PlayerList] formatPlayerForEveryone(ProxyPlayer): player=alowave2 vanished=false tabFormat=prefix=%luckperms_prefix% name=alowave2 suffix=%luckperms_suffix% featureDisabled=false tablistId=c88efb3b-af69-3051-b340-c555c9ce7e4e connectionState=CONNECTED
[22:15:09 INFO] [TAB]: [DEBUG] [PlayerList] Updating tablist for proxy player=alowave2 tablistId=c88efb3b-af69-3051-b340-c555c9ce7e4e formatComponent=me.neznamy.tab.shared.chat.component.LegacyTextComponent@74ebadf7 viewerCount=1
[22:15:09 INFO] [TAB]: [DEBUG] [PlayerList] Updating display name: viewer=alowave322 targetProxy=alowave2 format=me.neznamy.tab.shared.chat.component.LegacyTextComponent@74ebadf7
[22:15:09 INFO] [TAB]: [DEBUG] [UpdateDisableCondition] Processing: playerId=c88efb3b-af69-3051-b340-c555c9ce7e4e featureName=Tablist name formatting disabled=false knownProxyPlayers=1
[22:15:09 INFO] [TAB]: [DEBUG] [UpdateDisableCondition] Found target: name=alowave2 currentDisabled=false newDisabled=false connectionState=CONNECTED
[22:15:09 INFO] [TAB]: [DEBUG] [UpdateDisableCondition] State unchanged for player=alowave2 featureName=Tablist name formatting disabled=false, skipping dispatch
[22:15:10 INFO]: [/100.113.146.21:52452|kyousuke|1.21.11] <-> ServerConnector [lobby1] has connected
[22:15:10 INFO] [TAB]: [DEBUG] [Proxy Support] Encoding message PlayerJoin(uniqueId=9236bed8-f876-38c8-9618-8a6d64119c89, tablistId=9236bed8-f876-38c8-9618-8a6d64119c89, name=kyousuke, server=me.neznamy.tab.shared.data.Server@11c6e6da, vanished=false, staff=false, skin=null)
[22:15:10 INFO] [TAB]: [DEBUG] Player join of kyousuke processed in 1ms
[22:15:10 INFO] [TAB]: [DEBUG] Loaded MySQL data of kyousuke
[22:15:10 INFO] [TAB]: [DEBUG] Player kyousuke received scoreboard called sb-1, disabling TAB's scoreboard slotting.
[22:15:12 INFO]: [/100.113.146.21:52452|kyousuke|1.21.11|33ms] <-> ServerConnector [lobby2] has connected
[22:15:12 INFO] [TAB]: [DEBUG] [Proxy Support] Encoding message ServerSwitch(playerId=9236bed8-f876-38c8-9618-8a6d64119c89, newServer=me.neznamy.tab.shared.data.Server@66843de7)
[22:15:12 INFO]: [/100.113.146.21:52452|kyousuke|1.21.11|33ms] <-> DownstreamBridge <-> [lobby1] has disconnected
[22:15:12 INFO] [TAB]: [DEBUG] [PlayerList] onDisableConditionChange(TabPlayer): player=kyousuke disabledNow=false proxy=present currentDisabled=false
[22:15:12 INFO] [TAB]: [DEBUG] [PlayerList] Sending UpdateDisableCondition proxy message: player=kyousuke featureName=Tablist name formatting disabled=false
[22:15:12 INFO] [TAB]: [DEBUG] [Proxy Support] Encoding message UpdateDisableCondition(playerId=9236bed8-f876-38c8-9618-8a6d64119c89, featureName=Tablist name formatting, disabled=false)
[22:15:12 INFO] [TAB]: [DEBUG] [PlayerList] Feature re-enabled for local player=kyousuke, calling formatPlayerForEveryone
[22:15:12 INFO] [TAB]: [DEBUG] [Proxy Support] Encoding message PlayerListProxyPlayerData(id=2, playerId=9236bed8-f876-38c8-9618-8a6d64119c89, player=kyousuke, prefix=%luckperms_prefix%, name=kyousuke, suffix=%luckperms_suffix%)
[22:15:12 INFO] [TAB]: [DEBUG] [NameTag] onDisableConditionChange(TabPlayer): player=kyousuke disabledNow=false proxy=present teamName=CkyousukeA teamHandlingPaused=false currentDisabled=false
[22:15:12 INFO] [TAB]: [DEBUG] [NameTag] Sending UpdateDisableCondition proxy message: player=kyousuke featureName=NameTags disabled=false
[22:15:12 INFO] [TAB]: [DEBUG] [Proxy Support] Encoding message UpdateDisableCondition(playerId=9236bed8-f876-38c8-9618-8a6d64119c89, featureName=NameTags, disabled=false)
[22:15:12 INFO] [TAB]: [DEBUG] [NameTag] Feature re-enabled for local player=kyousuke, registering team=CkyousukeA
[22:15:13 INFO] [TAB]: [DEBUG] Player kyousuke received scoreboard called sb-1, disabling TAB's scoreboard slotting.


[22:14:56 INFO] [TAB]: [DEBUG] [Proxy Support] Decoded message LoadRequest()
[22:14:56 INFO] [TAB]: [DEBUG] [Proxy Support] Encoding message Load(decodedPlayers=[])
[22:14:58 INFO] [TAB]: [DEBUG] [Proxy Support] Decoded message PlayerJoin(uniqueId=bd17e774-ae01-3b24-9d4f-0ca6cf0e82fc, tablistId=bd17e774-ae01-3b24-9d4f-0ca6cf0e82fc, name=alowave322, server=me.neznamy.tab.shared.data.Server@65210b9d, vanished=false, staff=false, skin=null)
[22:14:59 INFO] [TAB]: [DEBUG] [PlayerList] onJoin(ProxyPlayer): player=alowave322 vanished=false tabFormat=null featureDisabled=false connectionState=CONNECTED
[22:14:59 INFO] [TAB]: [DEBUG] [PlayerList] formatPlayerForEveryone(ProxyPlayer): player=alowave322 vanished=false tabFormat=null featureDisabled=false tablistId=bd17e774-ae01-3b24-9d4f-0ca6cf0e82fc connectionState=CONNECTED
[22:14:59 INFO] [TAB]: [DEBUG] [PlayerList] Skipping: player=alowave322 tabFormat is null (not loaded yet)
[22:15:01 INFO]: [/100.119.181.62:52067|alowave2|1.21.11] <-> ServerConnector [lobby1] has connected
[22:15:01 INFO] [TAB]: [DEBUG] [Proxy Support] Encoding message PlayerJoin(uniqueId=c88efb3b-af69-3051-b340-c555c9ce7e4e, tablistId=c88efb3b-af69-3051-b340-c555c9ce7e4e, name=alowave2, server=me.neznamy.tab.shared.data.Server@65210b9d, vanished=false, staff=false, skin=null)
[22:15:01 INFO] [TAB]: [DEBUG] Player join of alowave2 processed in 6ms
[22:15:01 INFO] [TAB]: [DEBUG] Loaded MySQL data of alowave2
[22:15:01 INFO] [TAB]: [DEBUG] Player alowave2 received scoreboard called sb-1, disabling TAB's scoreboard slotting.
[22:15:05 INFO] [TAB]: [DEBUG] [Proxy Support] Decoded message ServerSwitch(playerId=bd17e774-ae01-3b24-9d4f-0ca6cf0e82fc, newServer=me.neznamy.tab.shared.data.Server@11a538dc)
[22:15:05 INFO] [TAB]: [DEBUG] [Proxy Support] Decoded message UpdateDisableCondition(playerId=bd17e774-ae01-3b24-9d4f-0ca6cf0e82fc, featureName=Tablist name formatting, disabled=false)
[22:15:05 INFO] [TAB]: [DEBUG] [UpdateDisableCondition] Processing: playerId=bd17e774-ae01-3b24-9d4f-0ca6cf0e82fc featureName=Tablist name formatting disabled=false knownProxyPlayers=1
[22:15:05 INFO] [TAB]: [DEBUG] [UpdateDisableCondition] Found target: name=alowave322 currentDisabled=false newDisabled=false connectionState=CONNECTED
[22:15:05 INFO] [TAB]: [DEBUG] [UpdateDisableCondition] State unchanged for player=alowave322 featureName=Tablist name formatting disabled=false, skipping dispatch
[22:15:05 INFO] [TAB]: [DEBUG] [Proxy Support] Decoded message PlayerListProxyPlayerData(id=1, playerId=bd17e774-ae01-3b24-9d4f-0ca6cf0e82fc, player=alowave322, prefix=%luckperms_prefix%, name=alowave322, suffix=%luckperms_suffix%)
[22:15:05 INFO] [TAB]: [DEBUG] [Proxy Support] Decoded message UpdateDisableCondition(playerId=bd17e774-ae01-3b24-9d4f-0ca6cf0e82fc, featureName=NameTags, disabled=false)
[22:15:05 INFO] [TAB]: [DEBUG] [PlayerList] formatPlayerForEveryone(ProxyPlayer): player=alowave322 vanished=false tabFormat=prefix=%luckperms_prefix% name=alowave322 suffix=%luckperms_suffix% featureDisabled=false tablistId=bd17e774-ae01-3b24-9d4f-0ca6cf0e82fc connectionState=CONNECTED
[22:15:05 INFO] [TAB]: [DEBUG] [PlayerList] Updating tablist for proxy player=alowave322 tablistId=bd17e774-ae01-3b24-9d4f-0ca6cf0e82fc formatComponent=me.neznamy.tab.shared.chat.component.LegacyTextComponent@79208d75 viewerCount=1
[22:15:05 INFO] [TAB]: [DEBUG] [PlayerList] Updating display name: viewer=alowave2 targetProxy=alowave322 format=me.neznamy.tab.shared.chat.component.LegacyTextComponent@79208d75
[22:15:05 INFO] [TAB]: [DEBUG] [UpdateDisableCondition] Processing: playerId=bd17e774-ae01-3b24-9d4f-0ca6cf0e82fc featureName=NameTags disabled=false knownProxyPlayers=1
[22:15:05 INFO] [TAB]: [DEBUG] [UpdateDisableCondition] Found target: name=alowave322 currentDisabled=false newDisabled=false connectionState=CONNECTED
[22:15:05 INFO] [TAB]: [DEBUG] [UpdateDisableCondition] State unchanged for player=alowave322 featureName=NameTags disabled=false, skipping dispatch
[22:15:09 INFO]: [/100.119.181.62:52067|alowave2|1.21.11|42ms] <-> ServerConnector [lobby2] has connected
[22:15:09 INFO]: [/100.119.181.62:52067|alowave2|1.21.11|42ms] <-> DownstreamBridge <-> [lobby1] has disconnected
[22:15:09 INFO] [TAB]: [DEBUG] [Proxy Support] Encoding message ServerSwitch(playerId=c88efb3b-af69-3051-b340-c555c9ce7e4e, newServer=me.neznamy.tab.shared.data.Server@11a538dc)
[22:15:09 INFO] [TAB]: [DEBUG] [PlayerList] onDisableConditionChange(TabPlayer): player=alowave2 disabledNow=false proxy=present currentDisabled=false
[22:15:09 INFO] [TAB]: [DEBUG] [PlayerList] Sending UpdateDisableCondition proxy message: player=alowave2 featureName=Tablist name formatting disabled=false
[22:15:09 INFO] [TAB]: [DEBUG] [NameTag] onDisableConditionChange(TabPlayer): player=alowave2 disabledNow=false proxy=present teamName=CJefferyEppsteiA teamHandlingPaused=false currentDisabled=false
[22:15:09 INFO] [TAB]: [DEBUG] [Proxy Support] Encoding message UpdateDisableCondition(playerId=c88efb3b-af69-3051-b340-c555c9ce7e4e, featureName=Tablist name formatting, disabled=false)
[22:15:09 INFO] [TAB]: [DEBUG] [NameTag] Sending UpdateDisableCondition proxy message: player=alowave2 featureName=NameTags disabled=false
[22:15:09 INFO] [TAB]: [DEBUG] [Proxy Support] Encoding message UpdateDisableCondition(playerId=c88efb3b-af69-3051-b340-c555c9ce7e4e, featureName=NameTags, disabled=false)
[22:15:09 INFO] [TAB]: [DEBUG] [PlayerList] Feature re-enabled for local player=alowave2, calling formatPlayerForEveryone
[22:15:09 INFO] [TAB]: [DEBUG] [NameTag] Feature re-enabled for local player=alowave2, registering team=CJefferyEppsteiA
[22:15:09 INFO] [TAB]: [DEBUG] [Proxy Support] Encoding message PlayerListProxyPlayerData(id=1, playerId=c88efb3b-af69-3051-b340-c555c9ce7e4e, player=alowave2, prefix=%luckperms_prefix%, name=alowave2, suffix=%luckperms_suffix%)
[22:15:10 INFO] [TAB]: [DEBUG] Player alowave2 received scoreboard called sb-1, disabling TAB's scoreboard slotting.
[22:15:10 INFO] [TAB]: [DEBUG] [Proxy Support] Decoded message PlayerJoin(uniqueId=9236bed8-f876-38c8-9618-8a6d64119c89, tablistId=9236bed8-f876-38c8-9618-8a6d64119c89, name=kyousuke, server=me.neznamy.tab.shared.data.Server@65210b9d, vanished=false, staff=false, skin=null)
[22:15:10 INFO] [TAB]: [DEBUG] [PlayerList] onJoin(ProxyPlayer): player=kyousuke vanished=false tabFormat=null featureDisabled=false connectionState=CONNECTED
[22:15:10 INFO] [TAB]: [DEBUG] [PlayerList] formatPlayerForEveryone(ProxyPlayer): player=kyousuke vanished=false tabFormat=null featureDisabled=false tablistId=9236bed8-f876-38c8-9618-8a6d64119c89 connectionState=CONNECTED
[22:15:10 INFO] [TAB]: [DEBUG] [PlayerList] Skipping: player=kyousuke tabFormat is null (not loaded yet)
[22:15:12 INFO] [TAB]: [DEBUG] [Proxy Support] Decoded message ServerSwitch(playerId=9236bed8-f876-38c8-9618-8a6d64119c89, newServer=me.neznamy.tab.shared.data.Server@11a538dc)
[22:15:12 INFO] [TAB]: [DEBUG] [Proxy Support] Decoded message UpdateDisableCondition(playerId=9236bed8-f876-38c8-9618-8a6d64119c89, featureName=NameTags, disabled=false)
[22:15:12 INFO] [TAB]: [DEBUG] [UpdateDisableCondition] Processing: playerId=9236bed8-f876-38c8-9618-8a6d64119c89 featureName=NameTags disabled=false knownProxyPlayers=2
[22:15:12 INFO] [TAB]: [DEBUG] [UpdateDisableCondition] Found target: name=kyousuke currentDisabled=false newDisabled=false connectionState=CONNECTED
[22:15:12 INFO] [TAB]: [DEBUG] [UpdateDisableCondition] State unchanged for player=kyousuke featureName=NameTags disabled=false, skipping dispatch
[22:15:12 INFO] [TAB]: [DEBUG] [Proxy Support] Decoded message UpdateDisableCondition(playerId=9236bed8-f876-38c8-9618-8a6d64119c89, featureName=Tablist name formatting, disabled=false)
[22:15:12 INFO] [TAB]: [DEBUG] [UpdateDisableCondition] Processing: playerId=9236bed8-f876-38c8-9618-8a6d64119c89 featureName=Tablist name formatting disabled=false knownProxyPlayers=2
[22:15:12 INFO] [TAB]: [DEBUG] [Proxy Support] Decoded message PlayerListProxyPlayerData(id=2, playerId=9236bed8-f876-38c8-9618-8a6d64119c89, player=kyousuke, prefix=%luckperms_prefix%, name=kyousuke, suffix=%luckperms_suffix%)
[22:15:12 INFO] [TAB]: [DEBUG] [UpdateDisableCondition] Found target: name=kyousuke currentDisabled=false newDisabled=false connectionState=CONNECTED
[22:15:12 INFO] [TAB]: [DEBUG] [UpdateDisableCondition] State unchanged for player=kyousuke featureName=Tablist name formatting disabled=false, skipping dispatch
[22:15:12 INFO] [TAB]: [DEBUG] [PlayerList] formatPlayerForEveryone(ProxyPlayer): player=kyousuke vanished=false tabFormat=prefix=%luckperms_prefix% name=kyousuke suffix=%luckperms_suffix% featureDisabled=false tablistId=9236bed8-f876-38c8-9618-8a6d64119c89 connectionState=CONNECTED
[22:15:12 INFO] [TAB]: [DEBUG] [PlayerList] Updating tablist for proxy player=kyousuke tablistId=9236bed8-f876-38c8-9618-8a6d64119c89 formatComponent=me.neznamy.tab.shared.chat.component.LegacyTextComponent@6196092d viewerCount=1
[22:15:12 INFO] [TAB]: [DEBUG] [PlayerList] Updating display name: viewer=alowave2 targetProxy=kyousuke format=me.neznamy.tab.shared.chat.component.LegacyTextComponent@6196092d