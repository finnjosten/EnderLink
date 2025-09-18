# EnderLink
A simple wel working connection system for connecting MC and Discord
  
  
## Supported versions
Bukkit `1.21.3`, created on Purpur 1.21.3, other versions could work but not tested.
  

## What can it do?
Ever heard of DiscordSRV? Well this is another take on that. You can send messages in discord or MC and let it show up in the other platform.  
Or want to show how big of a suckers your players are by only sending death messages? Yep thats possible!  
Want a channel to show if the server booted up or turned off? Yeah possible!
  
  
## How does it work?
Its not that difficult to understand.  
Lets define a few items:  
- Discord: the is your discord server/guild  
- Discord bot: this is the bot you have invited to your discord server and which sends the messages  
- Websocket or WSS: the server to which all the data is relayed  
- Minecraft: The server/plugin that is running in minecraft.  
  
so how it works is as follows:  
`MC event happens` > `sended to the WSS` > `Discord bot gets WSS event` > `Discord bot sends message in channel`  
Here is an example:  
`MC player sends a message` > `Plugin sees messages and relays it to the websocket` > `Discord gets the message from the websocket` > `Discord sends the message in the channel`  
  
  
## What do you need
You will need a discord bot up and running with the code from this repository: [finnjosten/EnderLinkDiscord](https://github.com/finnjosten/EnderLinkDiscord)  
You will need a websocket, this can be either our websocket or your own running this code: [finnjosten/EnderLinkWebsocket](https://github.com/finnjosten/EnderLinkWebsocket)  
  
  
## How do you set it up
Make sure you have the bot almost ready to go and the websocket is running/online.  
Using bukkit for your minecraft install you can just upload the plugin in the plugins folder.  
Then load the plugin either by restarting your server or using `/plugman load EnderLink-{version}` (this requires [PlugmanX](https://www.spigotmc.org/resources/plugmanx.88135/)).  
Now head on over to your plugins folder and find the EnderLink folder (`plugins/EnderLink`).  
Open the `config.yml`.  
Now scroll down till you find the room settings and copy the values of the `room-id` and `room-secret`.  
Now go to your discord bot.  
Open the `.env` for your bot (possibly you have to copy the `example.env` and rename it to `.env`).  
Fill the `ROOM_ID` and `ROOM_SECRET` with the values you copied from the config.  
Make sure to also fill the `CHANNEL_ID` with the id of the channel you want the bot to work in.
Now (re)start your bot.  
In the MC server console you should see that it has registered sucfully.  
Go to your discord server and send a message in the chosen channel, and see it popup in your MC server (console will also show it).  
Or just join the mc server and send some chats, they will show up in the discord channel!