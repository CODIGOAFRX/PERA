package com.peraerp.sales.masterdata;

import java.util.UUID;

/** Grupo de artículos de maestros, para rotular las reglas de comisión por grupo. */
public record ProductGroupSnapshot(UUID id, String code, String name) {
}
