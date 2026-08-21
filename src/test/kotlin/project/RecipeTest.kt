package org.example.project

import org.example.project.recipe.IngredientRepository
import org.example.project.recipe.IngredientRequest
import org.example.project.recipe.RecipeRepository
import org.example.project.recipe.RecipeRequest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Project 3: searching recipes by ingredient. */
class RecipeTest {

    @BeforeTest
    fun setUp() {
        DatabaseFactory.init("recipetest")
        DatabaseFactory.clearAll()
    }

    private fun seed() {
        val greenCurry = RecipeRepository.add(RecipeRequest("แกงเขียวหวานไก่", "ผัดพริกแกงแล้วใส่กะทิ"))
        IngredientRepository.add(greenCurry.id, IngredientRequest("chicken", 500.0, "g"))
        IngredientRepository.add(greenCurry.id, IngredientRequest("coconut milk", 400.0, "ml"))

        val friedRice = RecipeRepository.add(RecipeRequest("ข้าวผัดไก่", "ผัดข้าวกับไข่"))
        IngredientRepository.add(friedRice.id, IngredientRequest("Chicken breast", 200.0, "g"))
        IngredientRepository.add(friedRice.id, IngredientRequest("rice", 2.0, "cup"))

        val omelette = RecipeRepository.add(RecipeRequest("ไข่เจียว", "ตีไข่แล้วทอด"))
        IngredientRepository.add(omelette.id, IngredientRequest("egg", 2.0, "ฟอง"))
    }

    @Test
    fun `search returns every recipe using that ingredient`() {
        // Arrange
        seed()

        // Act
        val found = RecipeRepository.searchByIngredient("chicken")

        // Assert
        assertEquals(2, found.size, "both chicken dishes should match")
    }

    @Test
    fun `search ignores letter case`() {
        // "Chicken breast" is stored capitalised but must still match "chicken".
        seed()
        assertEquals(
            RecipeRepository.searchByIngredient("chicken").size,
            RecipeRepository.searchByIngredient("CHICKEN").size
        )
    }

    @Test
    fun `search matches part of an ingredient name`() {
        seed()
        assertTrue(RecipeRepository.searchByIngredient("coconut").isNotEmpty())
    }

    @Test
    fun `search for an unused ingredient returns an empty list, not an error`() {
        seed()
        assertEquals(emptyList(), RecipeRepository.searchByIngredient("beef"))
    }

    @Test
    fun `a recipe using an ingredient twice is still returned once`() {
        // Arrange
        val recipe = RecipeRepository.add(RecipeRequest("ซุปไก่สองรอบ", "..."))
        IngredientRepository.add(recipe.id, IngredientRequest("chicken thigh", 300.0, "g"))
        IngredientRepository.add(recipe.id, IngredientRequest("chicken stock", 500.0, "ml"))

        // Act
        val found = RecipeRepository.searchByIngredient("chicken")

        // Assert
        assertEquals(1, found.size, "distinct() must collapse the duplicate recipe")
    }

    @Test
    fun `ingredient cannot be attached to a recipe that does not exist`() {
        assertNull(IngredientRepository.add(999, IngredientRequest("salt", 1.0, "tsp")))
    }

    @Test
    fun `deleting a recipe cascades to its ingredients`() {
        // Arrange
        val recipe = RecipeRepository.add(RecipeRequest("จะถูกลบ", "..."))
        IngredientRepository.add(recipe.id, IngredientRequest("water", 1.0, "l"))

        // Act
        RecipeRepository.delete(recipe.id)

        // Assert
        assertEquals(0, IngredientRepository.getByRecipeId(recipe.id).size)
    }

    @Test
    fun `getWithIngredients nests only that recipe's ingredients`() {
        // Arrange
        seed()
        val target = RecipeRepository.getAll().first()

        // Act
        val full = RecipeRepository.getWithIngredients(target.id)

        // Assert
        assertEquals(2, full?.ingredients?.size)
        assertTrue(full!!.ingredients.all { it.recipeId == target.id })
    }
}
