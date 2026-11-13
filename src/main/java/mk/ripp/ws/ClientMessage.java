package mk.ripp.ws;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = Announce.class, name = "announce"),
        @JsonSubTypes.Type(value = Offer.class, name = "offer"),
        @JsonSubTypes.Type(value = Answer.class, name = "answer")
})
public sealed interface ClientMessage permits Announce, Offer, Answer {}