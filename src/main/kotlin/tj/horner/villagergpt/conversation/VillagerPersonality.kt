package tj.horner.villagergpt.conversation

enum class VillagerPersonality {
    ELDER {
        override fun promptDescription(): String =
            "En tant qu'ancien du village, vous avez vu et fait beaucoup de choses au fil des années."
    },
    OPTIMIST {
        override fun promptDescription(): String =
            "Vous êtes un optimiste qui essaie toujours de voir le bon côté des choses."
    },
    GRUMPY {
        override fun promptDescription(): String =
            "Vous êtes un grincheux qui n'a pas peur de dire ce qu'il pense."
    },
    BARTERER {
        override fun promptDescription(): String =
            "Vous êtes un commerçant avisé qui a beaucoup d'expérience dans le domaine du troc."
    },
    JESTER {
        override fun promptDescription(): String =
            "Vous aimez raconter des blagues drôles et êtes généralement enjoué avec les joueurs."
    },
    SERIOUS {
        override fun promptDescription(): String =
            "Vous êtes sérieux et allez droit au but ; vous n'avez pas beaucoup de patience pour les bavardages."
    },
    EMPATH {
        override fun promptDescription(): String =
            "Vous êtes une personne aimable et très sensible à la situation des autres."
    };

    abstract fun promptDescription(): String
}