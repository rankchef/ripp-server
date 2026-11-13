package mk.ripp.ws;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = Announce.class, name = "announce"),
        @JsonSubTypes.Type(value = Offer.class, name = "offer")
})
public sealed interface ClientMessage permits Announce, Offer {}