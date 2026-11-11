package com.moifinansy.core.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.BeachAccess
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Chair
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.ElectricBolt
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Laptop
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.LocalPharmacy
import androidx.compose.material.icons.rounded.LocalTaxi
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Percent
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Redeem
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.vector.ImageVector

/** Иконки хранятся в базе по имени Material Symbols; здесь — соответствие имени и картинки. */
object AppIcons {
    val byName: Map<String, ImageVector> = linkedMapOf(
        "shopping_cart" to Icons.Rounded.ShoppingCart,
        "restaurant" to Icons.Rounded.Restaurant,
        "local_cafe" to Icons.Rounded.LocalCafe,
        "directions_bus" to Icons.Rounded.DirectionsBus,
        "local_taxi" to Icons.Rounded.LocalTaxi,
        "directions_car" to Icons.Rounded.DirectionsCar,
        "local_gas_station" to Icons.Rounded.LocalGasStation,
        "home" to Icons.Rounded.Home,
        "electric_bolt" to Icons.Rounded.ElectricBolt,
        "water_drop" to Icons.Rounded.WaterDrop,
        "wifi" to Icons.Rounded.Wifi,
        "phone_android" to Icons.Rounded.PhoneAndroid,
        "medical_services" to Icons.Rounded.MedicalServices,
        "local_pharmacy" to Icons.Rounded.LocalPharmacy,
        "fitness_center" to Icons.Rounded.FitnessCenter,
        "spa" to Icons.Rounded.Spa,
        "checkroom" to Icons.Rounded.Checkroom,
        "chair" to Icons.Rounded.Chair,
        "build" to Icons.Rounded.Build,
        "child_care" to Icons.Rounded.ChildCare,
        "pets" to Icons.Rounded.Pets,
        "school" to Icons.Rounded.School,
        "book" to Icons.Rounded.Book,
        "sports_esports" to Icons.Rounded.SportsEsports,
        "movie" to Icons.Rounded.Movie,
        "music_note" to Icons.Rounded.MusicNote,
        "palette" to Icons.Rounded.Palette,
        "subscriptions" to Icons.Rounded.Subscriptions,
        "laptop" to Icons.Rounded.Laptop,
        "redeem" to Icons.Rounded.Redeem,
        "card_giftcard" to Icons.Rounded.CardGiftcard,
        "celebration" to Icons.Rounded.Celebration,
        "flight" to Icons.Rounded.Flight,
        "beach_access" to Icons.Rounded.BeachAccess,
        "percent" to Icons.Rounded.Percent,
        "payments" to Icons.Rounded.Payments,
        "credit_card" to Icons.Rounded.CreditCard,
        "account_balance_wallet" to Icons.Rounded.AccountBalanceWallet,
        "account_balance" to Icons.Rounded.AccountBalance,
        "savings" to Icons.Rounded.Savings,
        "storefront" to Icons.Rounded.Storefront,
        "work" to Icons.Rounded.Work,
        "attach_money" to Icons.Rounded.AttachMoney,
        "trending_up" to Icons.AutoMirrored.Rounded.TrendingUp,
        "more_horiz" to Icons.Rounded.MoreHoriz,
    )

    fun get(name: String): ImageVector = byName[name] ?: Icons.Rounded.MoreHoriz
}
