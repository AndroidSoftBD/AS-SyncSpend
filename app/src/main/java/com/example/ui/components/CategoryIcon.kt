package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object CategoryHelper {
    data class CategoryInfo(
        val name: String,
        val icon: ImageVector,
        val color: Color,
        val bgTint: Color
    )

    val categories = listOf(
        CategoryInfo("Food & Dining", Icons.Default.Restaurant, Color(0xFFF97316), Color(0x26F97316)),
        CategoryInfo("Transport", Icons.Default.DirectionsCar, Color(0xFF38BDF8), Color(0x2638BDF8)),
        CategoryInfo("Shopping", Icons.Default.ShoppingCart, Color(0xFFA855F7), Color(0x26A855F7)),
        CategoryInfo("Bills & Utilities", Icons.Default.Receipt, Color(0xFFF43F5E), Color(0x26F43F5E)),
        CategoryInfo("Entertainment", Icons.Default.Movie, Color(0xFFEC4899), Color(0x26EC4899)),
        CategoryInfo("Health", Icons.Default.FitnessCenter, Color(0xFF10B981), Color(0x2610B981)),
        CategoryInfo("Housing", Icons.Default.Home, Color(0xFFEAB308), Color(0x26EAB308)),
        CategoryInfo("Travel", Icons.Default.Flight, Color(0xFF06B6D4), Color(0x2606B6D4)),
        CategoryInfo("Salary", Icons.Default.AccountBalance, Color(0xFF10B981), Color(0x2610B981)),
        CategoryInfo("Investment", Icons.Default.TrendingUp, Color(0xFF8B5CF6), Color(0x268B5CF6)),
        CategoryInfo("Other", Icons.Default.Category, Color(0xFF94A3B8), Color(0x2694A3B8))
    )

    fun getCategoryInfo(name: String): CategoryInfo {
        return categories.firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: CategoryInfo(name, Icons.Default.Category, Color(0xFF94A3B8), Color(0x2694A3B8))
    }
}

@Composable
fun CategoryIconBadge(
    categoryName: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp
) {
    val info = CategoryHelper.getCategoryInfo(categoryName)
    Box(
        modifier = modifier
            .size(size)
            .background(color = info.bgTint, shape = CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = info.icon,
            contentDescription = categoryName,
            tint = info.color,
            modifier = Modifier.size(iconSize)
        )
    }
}
