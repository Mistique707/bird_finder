package com.example.birdfinder.ui.splash

/** A small bank of fun, family-friendly bird facts shown on the intro screen. */
object BirdFacts {
    val all: List<String> = listOf(
        "A group of crows is called a “murder.”",
        "Hummingbirds are the only birds that can fly backwards.",
        "Owls can rotate their heads about 270 degrees.",
        "A bird’s bones are hollow, helping keep it light for flight.",
        "The Arctic Tern migrates around 70,000 km round-trip each year.",
        "Crows can recognise and remember individual human faces.",
        "The bee hummingbird is the smallest bird — under 2 grams.",
        "Flamingos get their pink colour from the food they eat.",
        "A woodpecker can peck around 20 times per second.",
        "The peregrine falcon can dive at over 300 km/h.",
        "Some birds sleep with one eye open, resting half the brain.",
        "Kingfishers inspired the nose shape of Japan’s bullet train.",
        "The Indian peafowl (peacock) is the national bird of India.",
        "Mockingbirds can imitate dozens of other birds’ songs.",
        "Common swifts can stay airborne for months without landing.",
        "An eagle’s eyesight is roughly 4–8 times sharper than ours.",
        "Parrots can learn to mimic human speech and everyday sounds.",
        "The ostrich has the largest eye of any land animal.",
        "Pigeons can find their way home across hundreds of kilometres.",
        "Penguins are birds that can’t fly but swim superbly.",
    )

    fun random(): String = all.random()
}
