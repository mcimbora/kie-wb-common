/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kie.workbench.common.stunner.core.graph.command.impl;

import org.jboss.errai.common.client.api.annotations.MapsTo;
import org.jboss.errai.common.client.api.annotations.Portable;
import org.kie.workbench.common.stunner.core.command.CommandResult;
import org.kie.workbench.common.stunner.core.graph.Edge;
import org.kie.workbench.common.stunner.core.graph.Node;
import org.kie.workbench.common.stunner.core.graph.command.GraphCommandExecutionContext;
import org.kie.workbench.common.stunner.core.graph.command.GraphCommandResultBuilder;
import org.kie.workbench.common.stunner.core.graph.content.view.View;
import org.kie.workbench.common.stunner.core.graph.content.view.ViewConnector;
import org.kie.workbench.common.stunner.core.rule.RuleManager;
import org.kie.workbench.common.stunner.core.rule.RuleViolation;
import org.uberfire.commons.validation.PortablePreconditions;

import java.util.Collection;
import java.util.LinkedList;

/**
 * A Command to set the outgoing connection for an edge.
 * Note: if the connector's source is not set, the <code>sourceNode</code> can be null.
 * Note: This command can be used with edges that are not yet added into the graph (and index),
 * so this is why the constructor argument is an Edge instance, rather than the identifier. Anyway this instance
 * is marked as transient for not being serialized.
 */
@Portable
public final class SetConnectionSourceNodeCommand extends AbstractGraphCommand {

    private final String sourceNodeUUID;
    private final String edgeUUID;
    private final Integer magnetIndex;

    private String lastSourceNodeUUID;
    private Integer lastMagnetIndex;
    private transient Edge<? extends View, Node> edge;
    private transient Node<? extends View<?>, Edge> targetNode;

    @SuppressWarnings( "unchecked" )
    public SetConnectionSourceNodeCommand( @MapsTo( "sourceNodeUUID" ) String sourceNodeUUID,
                                           @MapsTo( "edge" ) Edge<? extends View, Node> edge,
                                           @MapsTo( "magnetIndex" ) Integer magnetIndex ) {
        this.edge = PortablePreconditions.checkNotNull( "edge", edge );
        this.edgeUUID = edge.getUUID();
        this.sourceNodeUUID = sourceNodeUUID;
        this.magnetIndex = PortablePreconditions.checkNotNull( "magnetIndex", magnetIndex );
        this.targetNode = edge.getTargetNode();
    }

    @Override
    public CommandResult<RuleViolation> allow( final GraphCommandExecutionContext context ) {
        return check( context );
    }

    @Override
    @SuppressWarnings( "unchecked" )
    public CommandResult<RuleViolation> execute( final GraphCommandExecutionContext context ) {
        final CommandResult<RuleViolation> results = check( context );
        if ( !results.getType().equals( CommandResult.Type.ERROR ) ) {
            final Node<?, Edge> sourceNode = getNode( context, sourceNodeUUID );
            final Edge<? extends View, Node> edge = getEdge( context );
            final Node<? extends View<?>, Edge> lastSourceNode = edge.getSourceNode();
            if ( null != lastSourceNode ) {
                this.lastSourceNodeUUID = lastSourceNode.getUUID();
                lastSourceNode.getOutEdges().remove( edge );
            }
            if ( null != sourceNode ) {
                sourceNode.getOutEdges().add( edge );
            }
            edge.setSourceNode( sourceNode );
            ViewConnector connectionContent = ( ViewConnector ) edge.getContent();
            lastMagnetIndex = connectionContent.getSourceMagnetIndex();
            connectionContent.setSourceMagnetIndex( magnetIndex );
        }
        return results;
    }

    @SuppressWarnings( "unchecked" )
    protected CommandResult<RuleViolation> doCheck( final GraphCommandExecutionContext context ) {
        final Node<View<?>, Edge> sourceNode = ( Node<View<?>, Edge> ) getNode( context, sourceNodeUUID );
        final Edge<View<?>, Node> edge = ( Edge<View<?>, Node> ) getEdge( context );
        final Collection<RuleViolation> connectionRuleViolations =
                ( Collection<RuleViolation> ) context.getRulesManager()
                        .connection().evaluate( edge, sourceNode, targetNode ).violations();
        final Collection<RuleViolation> cardinalityRuleViolations =
                ( Collection<RuleViolation> ) context.getRulesManager()
                        .edgeCardinality()
                        .evaluate( edge,
                                sourceNode,
                                targetNode,
                                sourceNode != null ? sourceNode.getOutEdges() : null,
                                targetNode != null ? targetNode.getInEdges() : null,
                                RuleManager.Operation.ADD )
                        .violations();
        final Collection<RuleViolation> violations = new LinkedList<RuleViolation>();
        violations.addAll( connectionRuleViolations );
        violations.addAll( cardinalityRuleViolations );
        return new GraphCommandResultBuilder( violations ).build();
    }

    @Override
    public CommandResult<RuleViolation> undo( final GraphCommandExecutionContext context ) {
        final SetConnectionTargetNodeCommand undoCommand =
                new SetConnectionTargetNodeCommand( lastSourceNodeUUID, getEdge( context ), lastMagnetIndex );
        return undoCommand.execute( context );
    }

    private Edge<? extends View, Node> getEdge( final GraphCommandExecutionContext context ) {
        if ( null == this.edge ) {
            this.edge = getViewEdge( context, edgeUUID );
        }
        return this.edge;
    }

    @Override
    public String toString() {
        return "SetConnectionSourceNodeCommand [edge=" + edgeUUID
                + ", candidate=" + ( null != sourceNodeUUID ? sourceNodeUUID : "null" )
                + ", magnet=" + magnetIndex + "]";
    }

}