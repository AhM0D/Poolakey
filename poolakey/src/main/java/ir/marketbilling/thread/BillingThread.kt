package ir.marketbilling.thread

internal interface BillingThread<TaskType> {

    fun execute(task: TaskType)

    fun dispose()

}
