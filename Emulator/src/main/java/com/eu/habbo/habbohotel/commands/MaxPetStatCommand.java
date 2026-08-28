package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.pets.PetManager;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.messages.outgoing.rooms.pets.PetTrainingPanelComposer;

public final class MaxPetStatCommand extends Command {
    public MaxPetStatCommand() { super("cmd_pet_info", new String[] {"maxpetstat"}); }

    @Override
    public boolean handle(GameClient gameClient, String[] params) {
        Room room = gameClient.getHabbo().getHabboInfo().getCurrentRoom();
        if (room == null || params.length != 2) return true;
        Pet pet = room.getCurrentPets().values().stream().filter(p -> p.getName().equalsIgnoreCase(params[1])).findFirst().orElse(null);
        if (pet == null || pet.getUserId() != gameClient.getHabbo().getHabboInfo().getId()) {
            gameClient.getHabbo().whisper("Il tuo pet deve essere presente nella stanza.", RoomChatMessageBubbles.ALERT);
            return true;
        }
        int maxLevel = PetManager.experiences.length + 1;
        pet.setExperience(PetManager.experiences[PetManager.experiences.length - 1]);
        pet.setLevel(maxLevel);
        pet.setEnergy(PetManager.maxEnergy(maxLevel));
        pet.setHappiness(100);
        pet.setLevelHunger(0);
        pet.setLevelThirst(0);
        pet.needsUpdate = true;
        gameClient.sendResponse(new PetTrainingPanelComposer(pet));
        gameClient.getHabbo().whisper("Pet potenziato: livello massimo, energia piena e tutti i comandi sbloccati.", RoomChatMessageBubbles.ALERT);
        return true;
    }
}
