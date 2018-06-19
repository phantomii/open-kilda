/* Copyright 2018 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.wfm.topology.ping.bolt;

import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.topology.ping.PingContext;

import org.apache.storm.tuple.Tuple;

public abstract class AbstractBolt extends org.openkilda.wfm.AbstractBolt {
    public static final String FIELD_ID_PING = "ping";

    protected PingContext getPingContext(Tuple input) throws PipelineException {
        PingContext context;
        try {
            context = (PingContext) input.getValueByField(FIELD_ID_PING);
        } catch (ClassCastException e) {
            throw new PipelineException(this, input, FIELD_ID_PING, e.toString());
        }
        return context;
    }
}