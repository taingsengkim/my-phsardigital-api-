package co.istad.projectpracticum.phsardigital.features.messaging;

/**
 * What kind of message a bubble is, so a client can pick a renderer without inspecting
 * which fields happen to be null.
 *
 * <p>Derived on the way out from whether a recording is attached, never stored. A stored
 * discriminator is a second source of truth for something the data already answers, and
 * the two drift the first time one write path forgets to set it.
 */
public enum MessageType {

    /** Typed text. */
    TEXT,

    /** A recording, with or without a caption alongside it. */
    VOICE
}
