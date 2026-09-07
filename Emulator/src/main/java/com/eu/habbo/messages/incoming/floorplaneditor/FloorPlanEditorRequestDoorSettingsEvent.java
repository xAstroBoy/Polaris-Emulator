package com.eu.habbo.messages.incoming.floorplaneditor;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.floorplaneditor.FloorPlanEditorDoorSettingsComposer;
import com.eu.habbo.messages.outgoing.rooms.RoomFloorThicknessUpdatedComposer;
import com.eu.habbo.messages.outgoing.rooms.RoomHeightMapComposer;

public class FloorPlanEditorRequestDoorSettingsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();

        if (room == null) return;

        this.client.sendResponse(new FloorPlanEditorDoorSettingsComposer(room));
        this.client.sendResponse(new RoomFloorThicknessUpdatedComposer(room));

        // The editor's live preview only starts once it has a baseline floor model to diff against, and it
        // takes that from the floor height map. That packet is otherwise only sent while entering the room,
        // long before the editor is opened, so the editor registered its handler too late to ever see one:
        // the baseline stayed empty and every edit was silently dropped instead of being drawn in the room.
        // Sending it after the door settings means the editor already knows the entry tile when it arrives.
        this.client.sendResponse(new RoomHeightMapComposer(room));
    }
}
