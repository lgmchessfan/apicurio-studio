/*
 * Copyright 2018 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.apicurio.hub.core.editing.ops.processors;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.apicurio.hub.core.editing.IEditingMetrics;
import io.apicurio.hub.core.editing.IEditingSession;
import io.apicurio.hub.core.editing.ISessionContext;
import io.apicurio.hub.core.editing.ops.BaseOperation;
import io.apicurio.hub.core.editing.ops.OperationFactory;
import io.apicurio.hub.core.editing.ops.VersionedOperation;
import io.apicurio.hub.core.storage.IStorage;
import io.apicurio.hub.core.storage.StorageException;

/**
 * @author Marc Savy {@literal <marc@rhymewithgravy.com>}
 */
@Singleton
public class UndoProcessor implements IOperationProcessor {

    private static Logger logger = LoggerFactory.getLogger(UndoProcessor.class);

    @Inject
    private IStorage storage;
    @Inject
    private IEditingMetrics metrics;

    /**
     * @see io.apicurio.hub.core.editing.ops.processors.IOperationProcessor#process(io.apicurio.hub.core.editing.IEditingSession, io.apicurio.hub.core.editing.ISessionContext, io.apicurio.hub.core.editing.ops.BaseOperation)
     */
    @Override
    public void process(IEditingSession editingSession, ISessionContext context, BaseOperation bo) {
        VersionedOperation undo = (VersionedOperation) bo;
        String user = editingSession.getUser(context);
        String designId = editingSession.getDesignId();

        long contentVersion = undo.getContentVersion();

        this.metrics.undoCommand(designId, contentVersion);

        logger.debug("\tuser:" + user);
        boolean reverted = false;
        try {
            reverted = storage.undoContent(user, designId, contentVersion);
        } catch (StorageException e) {
            logger.error("Error undoing a command.", e);
            // TODO do something sensible here - send a msg to the client?
            return;
        }

        // If the command wasn't successfully reverted (it was already reverted or didn't exist)
        // then return without doing anything else.
        if (!reverted) {
            return;
        }

        // Send an ack message back to the user
        editingSession.sendTo(OperationFactory.ack(contentVersion), context);
        logger.debug("ACK sent back to client.");

        // Now propagate the undo to all other clients
        editingSession.sendToOthers(OperationFactory.undo(contentVersion), context);
        logger.debug("Undo sent to 'other' clients.");
    }

    /**
     * @see io.apicurio.hub.core.editing.ops.processors.IOperationProcessor#getOperationName()
     */
    @Override
    public String getOperationName() {
        return "undo";
    }

}
