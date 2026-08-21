package org.example.project

import org.example.project.inventory.CategoryRepository
import org.example.project.inventory.CategoryRequest
import org.example.project.inventory.InventoryRuleException
import org.example.project.inventory.ProductRepository
import org.example.project.inventory.ProductRequest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Project 1: stock must never fall below zero. */
class InventoryTest {

    @BeforeTest
    fun setUp() {
        DatabaseFactory.init("inventorytest")
        DatabaseFactory.clearAll()
    }

    private fun aProduct(stock: Int = 10): Int {
        val category = CategoryRepository.add(CategoryRequest("เครื่องใช้ไฟฟ้า"))
        return ProductRepository.add(
            ProductRequest("หม้อหุงข้าว", "ขนาด 1.8 ลิตร", 1290.0, stock, category.id)
        )!!.id
    }

    @Test
    fun `product cannot be created under a category that does not exist`() {
        val created = ProductRepository.add(ProductRequest("ลอย", "ไม่มีหมวด", 100.0, 5, 999))
        assertNull(created, "a product needs a real category")
    }

    @Test
    fun `add-stock increases the quantity`() {
        // Arrange
        val id = aProduct(stock = 10)

        // Act
        val updated = ProductRepository.changeStock(id, 5)

        // Assert
        assertEquals(15, updated?.stockQuantity)
    }

    @Test
    fun `removing less stock than available succeeds`() {
        val id = aProduct(stock = 10)
        assertEquals(2, ProductRepository.changeStock(id, -8)?.stockQuantity)
    }

    @Test
    fun `removing exactly all the stock leaves zero and is allowed`() {
        // Zero is a valid stock level; only below zero is forbidden.
        val id = aProduct(stock = 10)
        assertEquals(0, ProductRepository.changeStock(id, -10)?.stockQuantity)
    }

    @Test
    fun `removing more stock than available is rejected and changes nothing`() {
        // Arrange
        val id = aProduct(stock = 10)

        // Act
        val error = assertFailsWith<InventoryRuleException> {
            ProductRepository.changeStock(id, -11)
        }

        // Assert
        assertTrue(error.message!!.contains("negative"))
        assertEquals(10, ProductRepository.getById(id)?.stockQuantity, "a rejected change must not be saved")
    }

    @Test
    fun `a product cannot be created with negative stock`() {
        val category = CategoryRepository.add(CategoryRequest("หมวดทดสอบ"))
        assertFailsWith<InventoryRuleException> {
            ProductRepository.add(ProductRequest("ติดลบ", "...", 50.0, -1, category.id))
        }
    }

    @Test
    fun `deleting a category cascades to its products`() {
        // Arrange
        val category = CategoryRepository.add(CategoryRequest("จะถูกลบ"))
        ProductRepository.add(ProductRequest("สินค้า A", "...", 10.0, 1, category.id))
        ProductRepository.add(ProductRequest("สินค้า B", "...", 20.0, 2, category.id))
        assertEquals(2, ProductRepository.getByCategory(category.id).size)

        // Act
        CategoryRepository.delete(category.id)

        // Assert
        assertEquals(0, ProductRepository.getByCategory(category.id).size)
    }

    @Test
    fun `changing stock of a product that does not exist returns null`() {
        assertNull(ProductRepository.changeStock(999, 5))
    }
}
