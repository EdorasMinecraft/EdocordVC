package com.github.kikisito.edocordvc;

import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.VoiceStateUpdateEvent;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.object.PermissionOverwrite;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.core.object.entity.channel.VoiceChannel;
import discord4j.core.object.presence.Presence;
import discord4j.discordjson.json.ActivityUpdateRequest;
import discord4j.rest.util.Permission;
import discord4j.rest.util.PermissionSet;

public class EdocordVC {

    public static void main(String[] args){
        final GatewayDiscordClient client = DiscordClientBuilder.create(System.getenv("discordcm_token"))
                .build()
                .login()
                .block();

        client.getEventDispatcher().on(ReadyEvent.class).subscribe(event -> {
            client.getSelf().subscribe(user -> client.updatePresence(Presence.online(ActivityUpdateRequest.builder().name("Edoras").type(3).build())).subscribe());

            for(String vcid : Config.vc_tc.keySet()){
                VoiceChannel vc = (VoiceChannel) client.getChannelById(Snowflake.of(vcid)).block();
                TextChannel tc = (TextChannel) client.getChannelById(Snowflake.of(Config.vc_tc.get(vcid))).block();
                for(PermissionOverwrite po : tc.getPermissionOverwrites()){
                    if(po.getType() == PermissionOverwrite.Type.MEMBER){
                        if(!vc.isMemberConnected(po.getMemberId().get()).block()){
                            tc.getOverwriteForMember(po.getMemberId().get()).get().delete().subscribe();
                        }
                    }
                }
            }
        });

        client.getEventDispatcher().on(VoiceStateUpdateEvent.class).subscribe(event -> {
            if(event.getOld().isPresent() && event.getOld().get().getChannelId().equals(event.getCurrent().getChannelId())) return;
            // Si se ha unido o movido a una sala de voz
            if(event.getCurrent().getChannel().block() != null){
                Snowflake id = event.getCurrent().getMember().block().getId();
                VoiceChannel vc = event.getCurrent().getChannel().block();
                if(Config.vc_tc.containsKey(vc.getId().asString())){
                    client.getChannelById(Snowflake.of(Config.vc_tc.get(vc.getId().asString()))).subscribe(((channel) -> {
                        TextChannel tc = (TextChannel) channel;
                        tc.addMemberOverwrite(id, PermissionOverwrite.forMember(id, PermissionSet.of(Permission.VIEW_CHANNEL), PermissionSet.none())).subscribe();
                    }));
                }
            }
            // Si se ha desconectado o movido a otra sala de voz
            if(event.getOld().isPresent()){
                Snowflake id = event.getOld().get().getMember().block().getId();
                VoiceChannel vc = event.getOld().get().getChannel().block();
                if(Config.vc_tc.containsKey(vc.getId().asString())){
                    client.getChannelById(Snowflake.of(Config.vc_tc.get(vc.getId().asString()))).subscribe((channel -> {
                        TextChannel tc = (TextChannel) channel;
                        if(tc.getOverwriteForMember(id).isPresent()) tc.getOverwriteForMember(id).get().delete().subscribe();
                        if(!vc.getVoiceStates().hasElements().block() && tc.getLastMessageId().isPresent()) {
                            // Exclude staff chat
                            if(tc.getId().equals(Snowflake.of("701226195466846258")) || tc.getId().equals(Snowflake.of("442679632928571394"))) return;
                            // Delete messages
                            tc.getMessagesBefore(tc.getLastMessageId().get()).subscribe(message -> message.delete().subscribe());
                        }
                    }));
                }
            }
        });
        client.onDisconnect().block();
    }
}
