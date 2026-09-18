package Producer.model;

/** Two independent storage technologies, not two more {@link PlantType} values -- a storage unit
 *  stores and releases energy rather than generating it, and folding it into PlantType would make
 *  every PlantType-keyed map (pricing, generation strategies) need a case it can't answer. */
public enum StorageKind {
    BATTERY,
    HYDROGEN
}
