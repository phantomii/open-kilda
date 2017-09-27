package org.bitbucket.openkilda.messaging.info.flow;

import static com.google.common.base.MoreObjects.toStringHelper;

import org.bitbucket.openkilda.messaging.Utils;
import org.bitbucket.openkilda.messaging.info.InfoData;
import org.bitbucket.openkilda.messaging.model.Flow;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.util.List;
import java.util.Objects;

/**
 * Represents flows northbound response.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "message_type",
        Utils.PAYLOAD})
public class FlowsResponse extends InfoData {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    /**
     * The response payload.
     */
    @JsonProperty(Utils.PAYLOAD)
    protected List<Flow> payload;

    /**
     * Instance constructor.
     *
     * @param payload response payload
     * @throws IllegalArgumentException if payload is null
     */
    @JsonCreator
    public FlowsResponse(@JsonProperty(Utils.PAYLOAD) List<Flow> payload) {
        setPayload(payload);
    }

    /**
     * Returns response payload.
     *
     * @return response payload
     */
    public List<Flow> getPayload() {
        return payload;
    }

    /**
     * Sets response payload.
     *
     * @param payload response payload
     */
    public void setPayload(List<Flow> payload) {
        if (payload == null) {
            throw new IllegalArgumentException("need to set payload");
        }
        this.payload = payload;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return toStringHelper(this)
                .add(Utils.PAYLOAD, payload)
                .toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(payload);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }

        FlowsResponse that = (FlowsResponse) object;
        return Objects.equals(getPayload(), that.getPayload());
    }
}