package app.olauncher.helper

import app.olauncher.helper.PhotoSet.Rename
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PhotoSetTest {

    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `keep-current index shift`() {
        val plan = PhotoSet.planRemoval(count = 5, index = 3, removed = setOf(0, 1))
        assertEquals(3, plan.newCount)
        assertEquals(1, plan.newIndex)
        assertFalse(plan.currentRemoved)
        assertEquals(listOf(0, 1), plan.removed)
        assertEquals(
            listOf(
                Rename(from = 2, to = 0),
                Rename(from = 3, to = 1),
                Rename(from = 4, to = 2)
            ),
            plan.renames
        )
    }

    @Test
    fun `current removed moves to next`() {
        val plan = PhotoSet.planRemoval(count = 5, index = 2, removed = setOf(2, 3))
        assertEquals(3, plan.newCount)
        assertEquals(2, plan.newIndex)
        assertTrue(plan.currentRemoved)
        assertEquals(listOf(2, 3), plan.removed)
        // Kept are 0, 1, 4. 0 and 1 keep their index (0->0, 1->1), so only 4->2 is in renames.
        assertEquals(listOf(Rename(from = 4, to = 2)), plan.renames)
    }

    @Test
    fun `wrap when current removed is at end`() {
        val plan = PhotoSet.planRemoval(count = 5, index = 4, removed = setOf(4))
        assertEquals(4, plan.newCount)
        assertEquals(0, plan.newIndex)
        assertTrue(plan.currentRemoved)
        assertEquals(listOf(4), plan.removed)
        // Kept are 0, 1, 2, 3. None shift index.
        assertTrue(plan.renames.isEmpty())
    }

    @Test
    fun `remove all`() {
        val plan = PhotoSet.planRemoval(count = 3, index = 1, removed = setOf(0, 1, 2))
        assertEquals(0, plan.newCount)
        assertEquals(0, plan.newIndex)
        assertTrue(plan.currentRemoved)
        assertEquals(listOf(0, 1, 2), plan.removed)
        assertTrue(plan.renames.isEmpty())
    }

    @Test
    fun `stale index coerced`() {
        // Stale index above count - 1 when kept: coerced to 4, kept is 4, newIndex 2
        val planHighKept = PhotoSet.planRemoval(count = 5, index = 10, removed = setOf(0, 1))
        assertEquals(2, planHighKept.newIndex)
        assertFalse(planHighKept.currentRemoved)

        // Stale index above count - 1 when removed: coerced to 4, removed 4, wraps to next kept 0
        val planHighRemoved = PhotoSet.planRemoval(count = 5, index = 8, removed = setOf(4))
        assertEquals(0, planHighRemoved.newIndex)
        assertTrue(planHighRemoved.currentRemoved)

        // Stale negative index when removed: coerced to 0, removed 0, next kept is 2 (at newIndex 0)
        val planLowRemoved = PhotoSet.planRemoval(count = 5, index = -5, removed = setOf(0, 1))
        assertEquals(0, planLowRemoved.newIndex)
        assertTrue(planLowRemoved.currentRemoved)

        // Stale negative index when kept: coerced to 0, kept 0, newIndex 0
        val planLowKept = PhotoSet.planRemoval(count = 5, index = -3, removed = setOf(2, 3))
        assertEquals(0, planLowKept.newIndex)
        assertFalse(planLowKept.currentRemoved)
    }

    @Test
    fun `each invalid input throws`() {
        // count <= 0
        assertThrows(IllegalArgumentException::class.java) {
            PhotoSet.planRemoval(count = 0, index = 0, removed = setOf(0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PhotoSet.planRemoval(count = -1, index = 0, removed = setOf(0))
        }

        // removed is empty
        assertThrows(IllegalArgumentException::class.java) {
            PhotoSet.planRemoval(count = 5, index = 0, removed = emptySet())
        }

        // any value in removed is outside 0 until count
        assertThrows(IllegalArgumentException::class.java) {
            PhotoSet.planRemoval(count = 5, index = 0, removed = setOf(-1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PhotoSet.planRemoval(count = 5, index = 0, removed = setOf(5))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PhotoSet.planRemoval(count = 5, index = 0, removed = setOf(0, 10))
        }
    }

    @Test
    fun `applyRemoval and countContiguous on distinct file contents`() {
        val dir = temporaryFolder.newFolder("wallpapers")

        // Initial state: 5 photos with distinct contents
        val contents = listOf(
            "photo_0_content",
            "photo_1_content",
            "photo_2_content",
            "photo_3_content",
            "photo_4_content"
        )
        for (i in contents.indices) {
            File(dir, "$i.jpg").writeText(contents[i])
        }

        assertEquals(5, PhotoSet.countContiguous(dir))

        // Plan: remove photos 0 and 1, current index 3
        val plan = PhotoSet.planRemoval(count = 5, index = 3, removed = setOf(0, 1))
        val result = PhotoSet.applyRemoval(dir, plan)

        assertTrue(result)
        assertEquals(3, PhotoSet.countContiguous(dir))

        // Assert by content that the right photo ended at each new index
        // Kept were 2, 3, 4. They should now be at 0, 1, 2.
        assertEquals("photo_2_content", File(dir, "0.jpg").readText())
        assertEquals("photo_3_content", File(dir, "1.jpg").readText())
        assertEquals("photo_4_content", File(dir, "2.jpg").readText())

        // 3.jpg and 4.jpg should no longer exist
        assertFalse(File(dir, "3.jpg").exists())
        assertFalse(File(dir, "4.jpg").exists())
    }

    @Test
    fun `applyRemoval with middle removals shifts correct content`() {
        val dir = temporaryFolder.newFolder("wallpapers_middle")

        val contents = listOf("p0", "p1", "p2", "p3", "p4")
        for (i in contents.indices) {
            File(dir, "$i.jpg").writeText(contents[i])
        }

        // Remove {2, 3} -> kept are 0, 1, 4
        val plan = PhotoSet.planRemoval(count = 5, index = 2, removed = setOf(2, 3))
        val result = PhotoSet.applyRemoval(dir, plan)

        assertTrue(result)
        assertEquals(3, PhotoSet.countContiguous(dir))
        assertEquals("p0", File(dir, "0.jpg").readText())
        assertEquals("p1", File(dir, "1.jpg").readText())
        assertEquals("p4", File(dir, "2.jpg").readText())
        assertFalse(File(dir, "3.jpg").exists())
        assertFalse(File(dir, "4.jpg").exists())
    }

    @Test
    fun `applyRemoval removes all files`() {
        val dir = temporaryFolder.newFolder("wallpapers_all")
        for (i in 0..2) {
            File(dir, "$i.jpg").writeText("content_$i")
        }

        val plan = PhotoSet.planRemoval(count = 3, index = 0, removed = setOf(0, 1, 2))
        val result = PhotoSet.applyRemoval(dir, plan)

        assertTrue(result)
        assertEquals(0, PhotoSet.countContiguous(dir))
        assertFalse(File(dir, "0.jpg").exists())
        assertFalse(File(dir, "1.jpg").exists())
        assertFalse(File(dir, "2.jpg").exists())
    }

    @Test
    fun `applyRemoval stops and returns false when file to delete does not exist`() {
        val dir = temporaryFolder.newFolder("wallpapers_missing")
        // Only create 1.jpg, 0.jpg is missing
        File(dir, "1.jpg").writeText("content_1")

        val plan = PhotoSet.planRemoval(count = 2, index = 0, removed = setOf(0, 1))
        val result = PhotoSet.applyRemoval(dir, plan)

        assertFalse(result)
        // 1.jpg should not have been deleted because it stopped at 0.jpg
        assertTrue(File(dir, "1.jpg").exists())
    }

    @Test
    fun `countContiguous handles missing dir and gaps`() {
        val nonExistentDir = File(temporaryFolder.root, "does_not_exist")
        assertEquals(0, PhotoSet.countContiguous(nonExistentDir))

        val dirWithGap = temporaryFolder.newFolder("gap_dir")
        // No 0.jpg yet
        assertEquals(0, PhotoSet.countContiguous(dirWithGap))

        // Create 0.jpg and 2.jpg (gap at 1)
        File(dirWithGap, "0.jpg").writeText("photo 0")
        File(dirWithGap, "2.jpg").writeText("photo 2")
        assertEquals(1, PhotoSet.countContiguous(dirWithGap))

        // Fill gap with 1.jpg
        File(dirWithGap, "1.jpg").writeText("photo 1")
        assertEquals(3, PhotoSet.countContiguous(dirWithGap))
    }

    @Test
    fun `applyRemoval continues past a failed rename and keeps later photos`() {
        val dir = temporaryFolder.newFolder("wallpapers_rename_fail")
        // 2.jpg is missing, so the rename 2 -> 1 fails
        File(dir, "0.jpg").writeText("p0")
        File(dir, "1.jpg").writeText("p1")
        File(dir, "3.jpg").writeText("p3")
        File(dir, "4.jpg").writeText("p4")

        val plan = PhotoSet.planRemoval(count = 5, index = 1, removed = setOf(0))
        val result = PhotoSet.applyRemoval(dir, plan)

        assertFalse(result)
        assertEquals("p1", File(dir, "0.jpg").readText())
        assertEquals("p3", File(dir, "2.jpg").readText())
        assertEquals("p4", File(dir, "3.jpg").readText())
        assertFalse(File(dir, "4.jpg").exists())
    }
}
