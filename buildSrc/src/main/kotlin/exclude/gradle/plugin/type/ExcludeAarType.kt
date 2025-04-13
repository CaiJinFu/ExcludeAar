package exclude.gradle.plugin.type

import exclude.gradle.plugin.*
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.AbstractCopyTask
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.bundling.Zip
import java.io.File


/**
 * 用来过滤aar
 */
class ExcludeAarType(private val project: Project) : ExcludeJarType(project) {

    override fun getFirstTaskNameOnlyTag(): String {
        return "excludeAar_"
    }

    /**
     * 解压aar之后文件存放的目录
     */
    private val tempAarDir by lazy {
        val tempAar = File(exPluginRootDir, "temp_aar")
        ensureFileExits(tempAar)
    }

    init {
        project.afterEvaluate { _ ->
            // each(Closure action)、all(Closure action)，但是一般我们都会用 all(...) 来进行容器的迭代。
            // all(...) 迭代方法的特别之处是，不管是容器内已存在的元素，还是后续任何时刻加进去的元素，都会进行遍历。
            val allTasks = mutableListOf<Task>()
            extension.aarsParams.all {
                require(!it.path.isNullOrEmpty()) {
                    "path 必须不为空:${it.path}"
                }

                allTasks.add(createTaskChain(it))
                implementation(it)
            }

            if (allTasks.isNotEmpty()) {
                createExAarPluginTasks(allTasks)
            }
        }
    }

    /**
     * 所有生产ex aar task
     */
    private fun createExAarPluginTasks(tasks: List<Task>) {
        project.task("excludeAarPluginTask") {
            it.group = "excludePlugin"
            it.setDependsOn(tasks)
        }
    }

    private fun implementation(extension: JarExcludeParam) {
        if (extension.implementation) {
            //找一下生成aar包的任务
            val task = (project.tasks.getByName("ex_aar_${extension.name?.trim()}${getTaskNameOnlyTag()}") as? AbstractArchiveTask)
            val aarFile = File(task?.destinationDir, task!!.archiveName)
            if (aarFile.exists()) {
                project.dependencies.run {
                    implementation(
                        project.files(aarFile)
                    )
                }
            }
        }
    }

    /**
     * 创建任务链
     */
    private fun createTaskChain(extension: AarExcludeParam) =
        createUnZipAar(extension)
            .then(createUnZipJar(extension))
            .then(createDeleteJars(extension))
            .then(createExJar(extension).apply {
                destinationDir = File(tempAarDir, extension.name!!)
            })
            .then(createExAar(extension))


    /**
     * 1、解压Aar
     * 2、解压aar中的jar
     * 3、删除已经被解压的jar
     */
    private fun createUnZipAar(extension: AarExcludeParam) =
        project.task<Copy>("unZip_aar_${extension.name?.trim()}${getTaskNameOnlyTag()}") {
            val unZipAarFile = File(tempAarDir, extension.name!!)
            //解压之后aar的存放目录
            from(project.zipTree(File(extension.path!!)))
            into(unZipAarFile)

            doLast {
                val jarFiles = mutableListOf<File>()

                if (unZipAarFile.exists()) {
                    unZipAarFile.walk().filter {
                        it.extension == "jar"
                    }.run {
                        jarFiles.addAll(this)
                    }
                }


                (project.tasks.getByName("unZip_jar_${extension.name?.trim()}${getTaskNameOnlyTag()}") as? AbstractCopyTask)?.from(
                    jarFiles.map {
                        project.zipTree(it)
                    })

                (project.tasks.getByName("delete_jars_${extension.name?.trim()}${getTaskNameOnlyTag()}") as? Delete)?.delete(
                    jarFiles
                )
            }

        }

    /**
     * 创建删除Jar的任务
     */
    private fun createDeleteJars(extension: AarExcludeParam) =
        project.task<Delete>("delete_jars_${extension.name?.trim()}${getTaskNameOnlyTag()}") {
        }


    private fun createExAar(extension: AarExcludeParam) =
        project.task<Zip>("ex_aar_${extension.name?.trim()}${getTaskNameOnlyTag()}") {
            baseName = "ex_${extension.name}"
            setExtension("aar")
            from(File(tempAarDir, extension.name!!))

            //需要排除的so
            exclude(extension.excludeSos)
            exclude(extension.excludeSoAbis)

            destinationDir = outputDir
        }
}