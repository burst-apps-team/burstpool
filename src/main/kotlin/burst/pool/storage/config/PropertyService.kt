package burst.pool.storage.config

import burst.kit.entity.BurstAddress

interface PropertyService {
    /**
     * Get the value of the specified property, or the default value if it was not set
     * @param prop The property to get the value of
     * @return The value of [prop] or its default value if it is not set
     */
    fun <T> get(prop: Prop<T>): T
}
