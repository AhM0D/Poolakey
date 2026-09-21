package ir.marketbilling.thread

internal interface PoolakeyThread<TaskType> {

    fun execute(task: TaskType)

    fun dispose()

}
